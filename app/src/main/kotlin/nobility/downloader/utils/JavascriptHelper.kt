@file:Suppress("ConstPropertyName")

package nobility.downloader.utils

object JavascriptHelper {

    const val LINK_RESPONSE_KEY = "LINKX"
    const val ERR_RESPONSE_KEY = "[ERR0R]"

    private const val LINK_KEY = "(link)"
    private const val CHANGE_KEY = "(change)"

    fun changeUrlToVideoFunction(
        functionChildLink: String
    ): String {
        return X.replace(LINK_KEY, functionChildLink)
    }

    /**
     * Get all video links using an edited version of a function found inside
     * the video frames source code.
     * Queries the url $LINK_KEY and prints the final response url so we can read it in the console
     * Selenium only shows the warn, error and severe levels by default.
     */
    private const val X = """
                    $.getJSON("$LINK_KEY", function(response) {
                        const vsd = response.enc;
                        const vhd = response.hd;
                        const vfhd = response.fhd;
                        const cdn = response.cdn;
                        const server = response.server;
                        if (vsd) {
                            console.warn('[$LINK_RESPONSE_KEY|vsd]' + server + '/getvid?evid=' + vsd)    
                        }
                        if (vhd) {
                            console.warn('[$LINK_RESPONSE_KEY|vhd]' + server + '/getvid?evid=' + vhd)    
                        }
                        if (vfhd) {
                            console.warn('[$LINK_RESPONSE_KEY|vfhd]' + server + '/getvid?evid=' + vfhd)    
                        }
                    })
                    .fail(function(jqXHR, textStatus, errorThrown) {
                        console.warn('$ERR_RESPONSE_KEY | ' + errorThrown)
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
                goToThis('$CHANGE_KEY');
            """

    fun changeUrlInternally(
        link: String
    ): String {
        return Y.replace(CHANGE_KEY, link)
    }

    const val SPAWN_MOVIE =
        """
// Regular expressions to extract player ID and report URL
const playerIdRegex = /\$\(\s*"#(?<playerId>[^"]+)"\s*\)\.attr\("src"/m;
const reportUrlRegex = /"(?<path>\/report\/\?[^"]+)"/m;

/**
 * Extracts player info and parameters from a parent HTML element.
 * @param {Element} parentElement - The parent HTML element containing script tags.
 * @returns {Object} - An object containing h, t, pid, and playerId.
 */
function extractPlayerData(parentElement) {
    const scriptTags = parentElement.querySelectorAll('script[type="text/javascript"]');
    let reportUrl, playerId;

    for (const script of scriptTags) {
        // Try to find a report URL
        const reportPath = reportUrlRegex.exec(script.innerText)?.groups?.path;
        if (reportPath) {
            reportUrl = new URL(reportPath, location.origin);
            // Try to find the player ID from the same script
            playerId = playerIdRegex.exec(script.innerText)?.groups?.playerId;
        }
    }

    if (!reportUrl) throw new Error("Report URL not found");
    if (!playerId) throw new Error("Player ID not found");

    const pid = reportUrl.searchParams.get("pid");
    const h = reportUrl.searchParams.get("h");
    const t = reportUrl.searchParams.get("t");

    if (!pid || !h || !t) throw new Error("Report URL is invalid");

    return {
        h,
        t,
        pid,
        playerId
    };
}

/**
 * Embeds a video iframe if not already present.
 */
function embedPlayer() {
    // Check if the iframe is already embedded
    const existingIframes = document.querySelectorAll('iframe[id^="frameNew"]');
    if (existingIframes.length > 0) return;

    // Look for the meta tag containing the embed URL
    const embedMetaTags = document.querySelectorAll('meta[itemprop="embedURL"]');
    if (embedMetaTags.length === 0) throw new Error("Embed URL element not found");

    for (const metaTag of embedMetaTags) {
        const embedFile = new URL(metaTag.content, location.origin).searchParams.get("file");
        if (!embedFile) throw new Error("Embed URL is invalid");

        const {
            h,
            t,
            pid,
            playerId
        } = extractPlayerData(metaTag.parentElement);

        // Create and configure the iframe
        const iframe = document.createElement("iframe");
        iframe.id = playerId;

        const iframeSrc = new URL("https://embed.watchanimesub.net/inc/embed/video-js.php");
        iframeSrc.searchParams.set("file", embedFile);
        iframeSrc.searchParams.set("fullhd", "1");
        iframeSrc.searchParams.set("pid", pid);
        iframeSrc.searchParams.set("h", h);
        iframeSrc.searchParams.set("t", t);
        iframeSrc.searchParams.set("embed", "neptun");

        iframe.src = iframeSrc.href;
        iframe.width = "530";
        iframe.height = "440";
        iframe.frameBorder = "0";
        iframe.scrolling = "no";
        iframe.setAttribute("webkitallowfullscreen", "true");
        iframe.setAttribute("mozallowfullscreen", "true");
        iframe.setAttribute("requestfullscreen", "true");
        iframe.setAttribute("msrequestfullscreen", "true");
        iframe.setAttribute("allowfullscreen", "");
        iframe.setAttribute("rel", "nofollow");
        iframe.setAttribute("data-type", "wco-embed");

        // Replace the sibling element before the meta tag with the iframe
        metaTag.previousElementSibling?.replaceWith(iframe);
    }
}

// Run embedPlayer only if on the correct domain
if (window.location.hostname === "www.wcostream.tv") {
    embedPlayer();
}

        """

}