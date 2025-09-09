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

    //thanks for not doing this in php brother
    const val HIDE_AD = """
      const announcement = document.getElementById('announcement');
      const backdrop = document.getElementById('backdrop');
      
	  function doClose() {
        if (announcement === null) {
            return;
        }
        announcement.style.opacity = '0';
        backdrop.style.opacity = '0';
        announcement.style.display = 'none';
        backdrop.style.display = 'none';
        var cerceve = "https://embed.wcostream.com/inc/embed/video-js.php" + window.location.search;
        window.location.replace(cerceve);
      }
	  

        doClose();
    """
}