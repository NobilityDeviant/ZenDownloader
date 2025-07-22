# ZenDownloader

A Kotlin & Jetpack Compose program used to download episodes, series and movies from: https://www.wcoflix.tv/

If you check that website out, it's one of the best video sites for free cartoons and anime. It's also the most vulnerable.

Many of the anime websites out there are locked down really hard which makes wco the perfect target.

**Now also supports M3U8 Videos & Episodes with 2 videos.**

*Free movie downloading has officially been killed*

*You will have to create a wcopremium.tv account and use those credentials in the settings now*

We're still back for blood. >:(

![](https://github.com/NobilityDeviant/ZenDownloader/raw/master/images/illegal_anime_poster.png)

# Donate

If you enjoy this project then please consider donating.

I would greatly appreciate it. :)

[<img src="images/donate_stripe.png?raw=true">](https://donate.stripe.com/6oEeV1aGb9lZgCIfYY)

# Requirements

**ZenDownloader** should now support Windows, Mac and Linux.

*If the new universal JAR doesn't work for you, you can still try to run it with IntelliJ: [Building](https://github.com/NobilityDeviant/ZenDownloader/blob/master/README.md#building)*

It has been tested on Windows 7, Windows 10, Windows 11, Ubuntu 22.04.04, Pop!_OS 22.04 and Fedora 42.

*These are tests with the old installer. The new JAR hasn't been fully tested yet.*

You will also need to install Chrome. This version doesn't support any other browsers.

*Windows Requires Visual C++:* [Visual C++ Redistributable for Visual Studio 2015](https://www.microsoft.com/en-us/download/details.aspx?id=48145)

# Download & Install

You won't need to download the JRE because it comes pre-packaged.

The new packages are all labelled for different operating systems.

They are each structured to be easily launchable with either a `run.bat` or a `run.sh` file.

These scripts will open the `launch.jar` which is used for easy updates.

**Note: You can launch the ZenDownloader JAR directly, but updates won't be supported.**

# Installing Chrome

*If you already have Chrome installed, make sure it's version 108 or higher.*

**For Windows:**

[Download & Install](https://www.google.com/chrome/?platform=windows)

**For Mac:**

[Download & Install](https://www.google.com/chrome/?platform=mac)

**For Linux (Debian):**

Run these commands in the terminal:

`sudo apt update`

`sudo apt install google-chrome-stable`

Input your password and you're done.

**For Linux (Red Hat Enterprise):**

`sudo dnf config-manager setopt google-chrome.enabled=1`

`sudo dnf install -y google-chrome-stable`

Input your password and you're done.

*You're on your own for any other version of Linux for now.*

# Download Release

**Download The Latest Release:** [Releases](https://github.com/NobilityDeviant/ZenDownloader/releases)

Choose the right `.zip` package for your operating system.

I'm not entirely sure how to check your PC hardware, but hopefully you already know.

*Most Windows Are AMD64. Windows ARM64 isn't directly supported*

*Apple Silicon = ARM64*

*Apple Intel = AMD64*

*AARCH64 = ARM64*

# Setup & Use Release

**For Windows:**

Extract the zip *here* and open the `run.bat` file.

**For Mac/Linux:**

Extract the zip *here*, open the extracted folder, and right click inside the folder and open the terminal.

If you can't do that, open the terminal somewhere else and redirect it to the extracted folder root.

Command Example: 

`cd /home/ZenDownloader-linux-amd64/`

If you're on Mac, you might need to bypass the Gatekeeper with the command:

`xattr -d com.apple.quarantine run.sh`

Now use the command:

`./run.sh`

If it fails to run due to permissions, use the command:

`chmod +x run.sh`

Input your password if it asks.

# Ffmpeg

M3U8 video files require ffmpeg to be installed in order to merge the ts files and merge the audio with the video.

**For Windows:**

Download the release build from: [https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full.7z](https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full.7z)

Extract it, open the `bin` folder and copy `ffmpeg.exe` into the database folder.

Example of path: `C:\Users\CuratedDev\.zen_database\ffmpeg.exe`

You can find the database folder in this guide: ([https://github.com/NobilityDeviant/ZenDownloader/blob/master/database/README.md#database-folder](https://github.com/NobilityDeviant/ZenDownloader/blob/master/database/README.md#database-folder))

*If you know how to set up ffmpeg as an environment variable, that will work too.*

**For Mac:**

*(AMD64 & Apple Silicon/ARM64)*

You can either install it using brew: [https://formulae.brew.sh/formula/ffmpeg](https://formulae.brew.sh/formula/ffmpeg)

If installing with brew, you shouldn't need to do anything else.

You can verify it's been installed in the terminal with the command:

`ffmpeg -version`

or

*(AMD64 Only)*

You can download it here: [https://evermeet.cx/ffmpeg/](https://evermeet.cx/ffmpeg/)

Extract it and copy `ffmpeg` into the database folder.

Example of path: `C:\Users\CuratedDev\.zen_database\ffmpeg`

You can find the database folder in this guide: ([https://github.com/NobilityDeviant/ZenDownloader/blob/master/database/README.md#database-folder](https://github.com/NobilityDeviant/ZenDownloader/blob/master/database/README.md#database-folder))

**For Linux:**

*Debian:*

Open the terminal and run the commanda:

`sudo apt update`

`sudo apt install ffmpeg`

Input your password and you're good.

You can verify it's been installed with the command:

`ffmpeg -version`

*Red Hat Enterprise:*

`sudo dnf update`

`sudo dnf install -y ffmpeg`

Input your password and you're good.

You can verify it's been installed with the command:

`ffmpeg --help`

*Hopefully you can find your command if you have any other linux version.*

# First Run

For your first run, you will be greeted with the **Asset Updater**

This will download all the files (besides images) from the [Database Folder](https://github.com/NobilityDeviant/ZenDownloader/tree/master/database)

If you want a better User Experience with the **Database Window** or if you want to see **Random Series**, you should also download the images.

A guide for that can be found here: [Download Images](https://github.com/NobilityDeviant/ZenDownloader/tree/master/database#series-images)

# Asset Updater Not Working

If the **Asset Updater** isn't working for you, then follow this guide to ensure you get all the updates: 

**Database Guide:** [Guide](https://github.com/NobilityDeviant/ZenDownloader/tree/master/database)

# Custom Chrome Browser & Driver

There's now an option to add your own path to the Chrome Browser and ChromeDriver.

First you're going to want to install Chrome from the offical website.
You can follow the [Download & Install](https://github.com/NobilityDeviant/ZenDownloader#download--install) for that.

You are going to need to find the version and the installed path of Chrome.

**Common Chrome Install Paths:**

*Windows:*

`C:\Program Files\Google\Chrome\Application\chrome.exe`
`C:\Program Files (x86)\Google\Chrome\Application\chrome.exe`
`%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe`

*Mac:*

`/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`

*Linux:*

`/usr/bin/google-chrome`
`/opt/google/chrome/google-chrome`
`/usr/bin/google-chrome-stable`
`/opt/google/chrome/google-chrome-stable`

**Find Chrome Version:**

*Universal:*

The Chrome version should be displayed inside the Settings of Chrome under *About Chrome*.

*Windows:*

You can navigate to the install folder, find chrome.exe and hover over it.
The Windows tooltip should show you the version.

*Mac:*

Open the terminal and use the command:

`/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --version`

Use the path to Chrome you found earlier.

Mac commands use `\` if the directory name contains a space. Make sure to include them.

*Linux:*

Open the terminal and use the command:

`google-chrome-stable --version`

or 

`google-chrome --version`

**Download Chrome Driver**

Now visit: [Chrome Driver Releases](https://googlechromelabs.github.io/chrome-for-testing/#stable)

and find the version closest to yours.

If you can't find an exact match, you can use the link it provides for your Operating System and replace the version with your own.

For example: [128.0.6613.85](https://storage.googleapis.com/chrome-for-testing-public/128.0.6613.85/win64/chromedriver-win64.zip)

**Settings**

Now inside **Settings** you will see 2 options:

*Chrome Browser Path*

and 

*Chrome Driver Path*

Click the **Set File** button for each path respectively to choose your files.

The browser path will be the `chrome.exe` you were using earlier to check the version.

The driver path will be the `chromedriver.exe` you just downloaded.

Linux will be a little bit different.

Linux chromedrivers will not have an extension.

The executable for Linux will be different as well. I have no clue where that would be, but I'll update this when I find out.

# MP4 Video Format

All videos get downloaded/converted to MP4 and I don't have intentions to allow different formats atm.

Make sure you have the correct codecs to watch mp4s.

Most operating systems do come with them, but some don't. 

Search Engines are your friends.

# UI

**Home**

![Home](images/home.png?raw=true "Home")

**Downloads**

![Downloads](images/downloads.png?raw=true "Downloads")

**Settings**

![Settings](images/settings.png?raw=true "Settings")

**Download Confirm**

![Download Confirm](images/download_confirm.png?raw=true "Download Confirm")

**Database**

![Database](images/database.png?raw=true "Database")

**Recent**

![Recent](images/recent.png?raw=true "Recent")

**History**

![History](images/history.png?raw=true "History")

# Side Tips

**Episode Multi-Select**

When you're in the **Download Confirm Window** you can multi-select specific episodes if there's a ton of them.

Hold the **Shift Key** to enable `Shift Mode` and click on the episode to highlight it. The first episode will be the starting point.

![ShiftMode1](guide/shift_mode_1.png?raw=true "ShiftMode1")

Now let go of the shift key, scroll down/up to whatever episode you want, hold the shift key again and select an episode.
This will highlight every episode in between the starting point to the last one.

![ShiftMode2](guide/shift_mode_2.png?raw=true "ShiftMode2")

Now once everything you want to select is highlighted, let go of the shift key and press the `Select % Highlighted Episode(s)` Button.
This will select everything that's been highlighted. It will lag, but just wait for it to finish.

![ShiftMode3](guide/shift_mode_3.png?raw=true "ShiftMode3")

**Timeout Errors**

If you are experiencing any timeout errors inside the `Error Console`, go to the `Settings Tab` and increase the timeout to something higher.
The timeout option effects everything related to timeouts and should help if you're having any issues.

![Timeout](guide/timeout.png?raw=true "Timeout")

# Building

You can also use this section if there isn't a distribution for your operating system.
As long as you can use IntelliJ, you're good to go.

I 100% recommend using IDEA. Don't use Eclipse or Netbeans because they're not fully supported.
This project is built using JDK 21.

Download IntelliJ: [Download](https://www.jetbrains.com/idea/)

Press the **Download** button, Scroll down and download the **Community Edition**.

For Windows & Mac, it should be as simple as opening the downloaded file.

For Linux, you have to extract the tar ball. 

Either right click it in the file and select **Extract Here** or find the terminal command for extracting tar balls.

Open the extracted folder, go into `bin`, right click an empty spot in the folder, open the Terminal and use the command:

`./idea`

In order to import the project through VCS, `Git` is needed as well.
*Git is recommended for to easy updating*

Download Git: [Download](https://git-scm.com/downloads)

Once those are installed open **IntelliJ**.

You will be greeted with the project window.

![Project Window](guide/idea_project_window.png?raw=true "Project Window")

Select **Get From VCS**

Scroll up and get the git url:

![Git Url](guide/get_git_link.png?raw=true "Get Git Link")

Paste it in the URL field and press the **Clone** button.

Once imported it will throw an error about the JDK if it's not installed.

Click the error in the console and you'll be greeted with this window:

![Git Url](guide/select_jdk.png?raw=true "Select JDK")

Under **Gradle JVM** select it and select **Download JDK**

For the version you will want to select **21**

Press **Download**, press **Apply** and press **Ok**

Once that's been downloaded, go to File > Close Project

![Close Project](guide/close_project.png?raw=true "Close Project")

and then reopen it and you should be good to go.

If there's a warning on top of IntelliJ to install Java, you have to accept it.

Now once everything is imported and downloaded, open the project tree on the left (Folder Icon) and look for: 

`src > main > kotlin > Main.kt`

Right click **Main.kt** and select `Run MainKt`

Or even better, you can use the `run` task from Gradle under `ZenDownload > Tasks > compose desktop`.

**Creating OS distributables is no longer supported or encouraged**

You can however create the JAR distributables if you want with the Gradle Task under:

`ZenDownloader > Tasks > custom jar > packageJARDistributables`

**Note: This will download all the JDKs for every operating system that will total in about 2GB of data.**

That's it! If you have any issues, please create an issue in Github and i'll get right on it.
