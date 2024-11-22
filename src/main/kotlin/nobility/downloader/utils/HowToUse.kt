package nobility.downloader.utils

import nobility.downloader.core.Core

object HowToUse {

    val text = """
                  You need to visit: ${Core.wcoUrl}
                  and pick out your favorite anime, cartoon or movie.
                                             
                  Once you find one that you want, you copy the link in the url bar and paste it into the programs Home page.
                  
                  You can also look through the database window by pressing CTRL + W to find your shows.
                  
                  If the text inside the url bar isn't a URL, it will search for it inside the database window.
                  
                  After it's filled, you press the Start button or hit the Enter key on your keyboard.
                  There is also right click options in the url field to help you out.
                                            
                  After the program scrapes the series details, it will open the Download Window where you can select the episodes you want to download.
                                            
                  Once you have selected some episodes, press the Download (X) Episodes button and let the program do it's thing.
                                            
                  You can view the download progress in the Downloads page by pressing the download icon in the home page or press CTRL + D.
                                            
                  Don't forget to visit the Settings page and tweak your options.
                                            
                  Settings can be viewed with pressing the Settings icon in the home page on the top left or by pressing CTRL + S.
               """.trimIndent()
}