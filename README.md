# ZenDownloader

A Kotlin & Jetpack Compose program used to download episodes, series and movies from: https://www.wcofun.net/

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

I have changed the donations to a custom Stripe page.

[Donate](https://donate.stripe.com/6oEeV1aGb9lZgCIfYY)

# Requirements

**ZenDownloader** currently only supports Windows and Debian/RHEL based Linux.

*You can still try to run it with IntelliJ if your OS supports it: [Building](https://github.com/NobilityDeviant/ZenDownloader/blob/master/README.md#building)*

It has been tested on Windows 7, Windows 10, Windows 11, Ubuntu 22.04.04, Pop!_OS 22.04 and Fedora 42.

You will also need to install Chrome. This version doesn't support any other browsers right now.

# Download & Install

You won't need to download the JRE because it comes pre-packaged.

# Ffmpeg

M3U8 video files require ffmpeg to be installed in order to merge the ts files and merge the audio with the video.

**For Windows:** 

Download the release build from: [https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full.7z](https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full.7z)

Extract it, open the `bin` folder and copy `ffmpeg.exe` into the database folder.

Example of path: `C:\Users\CuratedDev\.zen_database\ffmpeg.exe`

You can find the database folder in this guide: ([https://github.com/NobilityDeviant/ZenDownloader/blob/master/database/README.md#database-folder](https://github.com/NobilityDeviant/ZenDownloader/blob/master/database/README.md#database-folder))

**If you know how to set up ffmpeg as an environment variable, that will work too.**

**For Linux:**

**Debian:**

Open the terminal and run the commanda:

`sudo apt update`

`sudo apt install ffmpeg`

Input your password and you're good.

You can verify it's been installed with the command:

`ffmpeg -version`

**Red Hat Enterprise:**

`sudo dnf update`

`sudo dnf install -y ffmpeg`

Input your password and you're good.

You can verify it's been installed with the command:

`ffmpeg --help`

# Windows

**Download & Install Chrome:** [Download](https://www.google.com/chrome/?platform=windows)

*If you already have Chrome installed, make sure it's version 108 or higher.*

**Download & Install The Latest Release:** [Releases](https://github.com/NobilityDeviant/ZenDownloader/releases)

For Windows it'll be the file with the `.exe` extension.

You're also going to need to install: [Visual C++ Redistributable for Visual Studio 2015](https://www.microsoft.com/en-us/download/details.aspx?id=48145)

# Linux (Debian)

**Download & Install Chrome:**

Download Chrome Here: [Download](https://www.google.com/chrome/?platform=linux)

*If you already have Chrome installed, make sure it's version 108 or higher.*

Choose **64 bit .deb (For Debian/Ubuntu)**

Once downloaded, go to the folder it's been downloaded to, right click an empty space in the window an open the **Terminal** app.

Now inside the terminal you will type:

`sudo apt install ./chrome.deb`

Replacing `chrome.deb` with the file name.

Input your password and you're done.

You should also be able to install through the Terminal with the commands:

`sudo apt update`

`sudo apt install google-chrome-stable`

# Linux (Red Hat Enterprise)

**Download & Install Chrome:**

Run these commands in the terminal:

`sudo dnf config-manager setopt google-chrome.enabled=1`

`sudo dnf install -y google-chrome-stable`

Input your password and you're done.

*If you already have Chrome installed, make sure it's version 108 or higher with:*

`google-chrome-stable --version`

**Download & Install The Latest Release:**

Download The Latest Release Here: [Releases](https://github.com/NobilityDeviant/ZenDownloader/releases)

For Red Hat Enterprise it'll be the with the `.rpm` extension.

Go to the folder it's been downloaded to, right click an empty space in the window an open the **Terminal** app.

Now inside the terminal you will type:

`sudo dnf install -y ./zendownloader.rpm`

Replacing `zendownloader.rpm` with the file name.

Input your password and you're done.

If you wish to uninstall it, you can use the command:

`sudo dnf remove zendownloader` or `sudo dnf remove ZenDownloader` if it complains about casing.

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
This works on both WIndows & Linux.

First you're going to want to install Chrome from the offical website.
You can follow the [Download & Install](https://github.com/NobilityDeviant/ZenDownloader#download--install) for that.

Now when Chrome is installed, you need to find the version of it.

**Windows**

In your Chrome folder there's going to be different folders with different versions if you have multiple ones.

Delete all the older version folders if you have any.

![Settings](images/chrome_browser_path_windows.png?raw=true "Windows Chrome Browser Versions")

Either copy the latest versions folder name or hover over the `chrome.exe`
and use the version the tooltip provides.

**Linux**

You can find your Chrome verion with this command:

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

# Cloudflare

Wco is now using cloudflare. Most VPNs and public proxies won't work anymore!
I will also have to keep track and update the useragents every now and then.

If you encounter any cloudflare error, just keep trying or report it.

Don't worry too much though. 

**ZenDownloader** comes equipped with a Kotlin version of Undetected ChromeDriver which bypasses all basic cloudflare blocking.

The only way cloudflare would work is if wco pays for their premium package, which is expensive.

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

Or even better, you can use the `run` task from Gradle.

If you want to make the `.deb` or the `.rpm` file, you need to install a couple extra things:

Debian:

`sudo apt install binutils`

`sudo apt install fakeroot`

Red Hat Enterprise:

`sudo dnf install binutils`

`sudo dnf install fakeroot`

`sudo dnf install rpm-build`

To create the distribution files, you have to use the gradle task on the right:

![Gradle Distribution](guide/gradle_build_distribution.png?raw=true "Gradle Distribution")

Choose your respective extension to build by double-clicking **packageExe**, **packageDeb** or **packageRpm** under the **Tasks** section.

After everything is set up for the first time, all you'd have to do is update the project files for any major commit.

![Update Project](guide/update_project.png?raw=true "Update Project")

That's it! If you have any issues, please create an issue in Github and i'll get right on it. (Will be delayed due to other projects atm)

So much readme commits.. Sorry about that.
