package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

object FutureUtil {
    fun disinterest(future: CompletableFuture<*>): CompletableFuture<Void> {
        val f = CompletableFuture<Void>()
        future.whenComplete { _: Any?, ex: Throwable? ->
            if (null != ex) {
                f.completeExceptionally(ex)
            } else {
                f.complete(null)
            }
        }
        return f
    }

    /**
     * Returns a new CompletableFuture that is completed when all the given CompletableFutures complete.
     * Different from [CompletableFuture.allOf], if all the given CompletableFutures
     * complete normally or exceptionally, then the returned CompletableFuture also does so, the results of the
     * given CompletableFutures are not reflected in the returned CompletableFuture, but may be obtained by inspecting
     * them individually.
     *
     *
     * If no CompletableFutures are provided, returns a CompletableFuture completed with the value [CombineFutureResult.NORMAL].
     */
    private fun allOf(vararg cfs: CompletableFuture<*>): CompletableFuture<CombineFutureResult> {
        return andTree(cfs, 0, cfs.size - 1)
    }

    fun allOfColl(
        cfs: Collection<CompletableFuture<*>>
    ): CompletableFuture<CombineFutureResult> {
        return allOf(
            *cfs.toTypedArray<CompletableFuture<*>>()
        )
    }


    @Suppress("warnings")
    private fun andTree(
        cfs: Array<out CompletableFuture<*>>,
        lo: Int,
        hi: Int
    ): CompletableFuture<CombineFutureResult> {
        val r: CompletableFuture<CombineFutureResult>
        if (lo > hi) {
            r = CompletableFuture.completedFuture(CombineFutureResult.NORMAL)
        } else {
            var a: CompletableFuture<*>
            var b: CompletableFuture<*>? = null
            val mid = (lo + hi) ushr 1
            if (((if (lo == mid) cfs[lo] else andTree(cfs, lo, mid)).also { a = it }) == null ||
                ((if (lo == hi) a else if ((hi == mid + 1)) cfs[hi] else andTree(cfs, mid + 1, hi)).also {
                    b = it
                }) == null
            ) {
                throw NullPointerException()
            }
            r = normalRelay(a, b)
        }
        return r
    }

    private fun normalRelay(
        aFuture: CompletableFuture<*>?,
        bFuture: CompletableFuture<*>?
    ): CompletableFuture<CombineFutureResult> {
        val r = CompletableFuture<CombineFutureResult>()
        val aResult = AtomicReference<CombineFutureResult?>()
        val bResult = AtomicReference<CombineFutureResult?>()
        if (null == aFuture) {
            aResult.set(null)
            if (null == bFuture) {
                bResult.set(null)
                applyResult(r, aResult, bResult)
            } else {
                bFuture.whenComplete { v: Any, ex: Throwable? ->
                    digResult(bResult, v, ex)
                    applyResult(r, aResult, bResult)
                }
            }
        } else {
            if (null == bFuture) {
                bResult.set(null)
                aFuture.whenComplete { v: Any, ex: Throwable? ->
                    digResult(aResult, v, ex)
                    applyResult(r, aResult, bResult)
                }
            } else {
                aFuture.whenComplete { aV: Any, aEx: Throwable? ->
                    digResult(aResult, aV, aEx)
                    bFuture.whenComplete { v: Any, ex: Throwable? ->
                        digResult(bResult, v, ex)
                        applyResult(r, aResult, bResult)
                    }
                }
            }
        }
        return r
    }

    private fun applyResult(
        r: CompletableFuture<CombineFutureResult>,
        aResult: AtomicReference<CombineFutureResult?>,
        bResult: AtomicReference<CombineFutureResult?>
    ): CompletableFuture<CombineFutureResult> {
        val res: CombineFutureResult
        val aar = aResult.get()
        val bbr = bResult.get()
        res = if (null == aar) {
            bbr ?: CombineFutureResult.NORMAL
        } else {
            if (null == bbr) {
                aar
            } else {
                if (aar == CombineFutureResult.NORMAL && bbr == CombineFutureResult.NORMAL) {
                    CombineFutureResult.NORMAL
                } else if (aar == CombineFutureResult.ALL_EXCEPTION && bbr == CombineFutureResult.ALL_EXCEPTION) {
                    CombineFutureResult.ALL_EXCEPTION
                } else {
                    CombineFutureResult.EXCEPTION
                }
            }
        }
        r.complete(res)
        return r
    }

    private fun digResult(result: AtomicReference<CombineFutureResult?>, r: Any, thr: Throwable?) {
        if (null == thr) {
            result.set(CombineFutureResult.NORMAL)
            if (r is CombineFutureResult) {
                result.set(r)
            }
        } else {
//            thr.printStackTrace();
            result.set(CombineFutureResult.ALL_EXCEPTION)
        }
    }

    enum class CombineFutureResult {
        NORMAL, EXCEPTION, ALL_EXCEPTION
    }
}
