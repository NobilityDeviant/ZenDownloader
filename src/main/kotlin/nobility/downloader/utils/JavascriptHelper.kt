package nobility.downloader.utils

import nobility.downloader.core.settings.Quality

private const val LINK_KEY = "(link)"
private const val RES_KEY = "(res)"

object JavascriptHelper {

    fun changeUrlToVideoFunction(
        functionChildLink: String,
        quality: Quality
    ): String {
        return X
            .replace(LINK_KEY, functionChildLink)
            .replace(RES_KEY, quality.htmlText)
    }

    fun changeUrl(
        newUrl: String
    ): String {
        return Y.replace(RES_KEY, newUrl)
    }

    /**
     * Get all video links using an edited version of a function found inside
     * the video frames source code.
     * Queries the url and redirects to that url so we can extract it with Selenium.
     */
    private const val X = """
                $.getJSON("$LINK_KEY", function(response) {
                    vsd = response.enc;
                    vhd = response.hd;
                    vfhd = response.fhd;
                    cdn = response.cdn;
                    server = response.server;
                    location.href = server + '/getvid?evid=' + $RES_KEY
                });
            """

    @Suppress("UNUSED")
    private const val xx = """
                fetch("$LINK_KEY")
                    .then(res => res.json())
                    .then(function (response) {
                        vsd = response.enc;
                        vhd = response.hd;
                        vfhd = response.fhd;
                        cdn = response.cdn;
                        server = response.server;
                        location.href = server + '/getvid?evid=' + $RES_KEY
                    })
                    .catch(function() {
                        
                    });
            """

    /**
     * Redirect to another link internally so certain blocking methods won't work.
     */
    private const val Y = """
                window.goToThis = function(url) {
                    var link = document.createElement("a");
                    link.setAttribute("href", url);
                    link.style.display = "none";
                    document.body.appendChild(link);
                    link.click();
                }
                goToThis('$RES_KEY');
            """

}