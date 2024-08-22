# ZenDownloader

A Kotlin & Jetpack Compose program used to download videos and series from: https://www.wcofun.net/

If you check that website out, it's one of the best video sites for free cartoons and anime. It's also the most vulnerable.

Many of the anime websites out there are locked down really hard which makes wco the perfect target.

![](https://github.com/NobilityDeviant/ZenDownloader/raw/master/images/illegal_anime_poster.png =250x250)

# Donate

If you enjoy this project then please consider donating.

I would greatly appreciate it. :)

[Donate](https://buymeacoffee.com/nobilitydeviant)

# Requirements

ZenDownloader currently only supports Windows and Debian based Linux. x64 Only.

It has been tested on Windows 10, Windows 11 and Ubuntu 22.04.04

You will also need to install Chrome. This version doesn't support any other browsers right now.

# Download

You won't need to download anything extra like the JRE because it comes pre-packaged.

# Windows

**Download & Install Chrome:** [Download](https://www.google.com/chrome/?platform=windows)

**Download & Install The Latest Release:** [Releases](https://github.com/NobilityDeviant/ZenDownloader/releases)

For Windows it'll be the file with the `.exe` extension.

# Linux (Debian)

**Download & Install Chrome:**

Download Chrome Here: [Download](https://www.google.com/chrome/?platform=linux)

Choose *64 bit .deb (For Debian/Ubuntu)*

Once downloaded, go to the folder it's been downloaded to, right click an empty space in the window an open the **Terminal** app.

Now inside the terminal you will type:

`sudo apt-get install ./chrome.deb`

Replacing `chrome.deb` with the file name.

Input your password and you're done.

**Download & Install The Latest Release:**

Download The Latest Release Here: [Releases](https://github.com/NobilityDeviant/ZenDownloader/releases)

For Debian it'll be the with the `.deb` extension.

Like Chrome, go to the folder it's been downloaded to, right click an empty space in the window an open the **Terminal** app.

Now inside the terminal you will type:

`sudo apt-get install ./zendownloader.deb`

Replacing `zendownloader.deb` with the file name.

Input your password and you're done.

Once opened if you use a keyring, it will ask for the password.
After denying it I have found no issues, so this isn't needed afaik.

Learn more about the keyring here: https://wiki.gnome.org/action/show/Projects/GnomeKeyring?action=show&redirect=GnomeKeyring

# First Run

For your first run you will be greeted with the *Asset Updater*

This will download all the files (besides images) from the [Database Folder](https://github.com/NobilityDeviant/ZenDownloader/tree/master/database)

Once that's complete it will run additional checks for genres, movies and urls.

These all happen in the background and require Chrome so be sure that's installed first.

If you want a better User Experience with the *Database Window* you should also download the images.

A guide for that can be found here: [Download Images](https://github.com/NobilityDeviant/ZenDownloader/tree/master/database#series-images)

# Cloudflare

Wco is now using cloudflare. Most VPNs and public proxies won't work anymore!
I will also have to keep track and update the useragents every now and then.

If you encounter any cloudflare errors, just keep trying or report it.

**ZenDownloader** comes equipped with a Kotlin version of Undetected ChromeDriver which bypasses all basic cloudflare blocking.

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

# Building

I 100% recommend using IDEA. Don't use Eclipse or Netbeans because they're not fully supported.
This project is built using JDK 17, though I'm sure higher versions work as well.

Download IntelliJ: [Download](https://www.jetbrains.com/idea/)

In order to import the project through VCS, `Git` is needed as well.

Download Git: [Download](https://git-scm.com/downloads)

Once those are installed open *IntelliJ*.

You will be greeted with the project window.

![Project Window](guide/idea_project_window.png?raw=true "Project Window")

Select *Get From VCS*

Scroll up and get the git url:

![Git Url](guide/get_git_link.png?raw=true "Get Git Link")

Paste it in the URL field and press the *Clone* button.

Once imported it will throw an error about the JDK if it's not installed.

Click the error in the console and you'll be greeted with this window:

![Git Url](guide/select_jdk.png?raw=true "Select JDK")

Under *Gradle JVM* select it and select *Download JDK* 

For the version you will want to select *17*

Press *Download*, press *Apply* and press *Ok*

Once that's been downloaded, go to File > Close Project

![Close Project](guide/close_project.png?raw=true "Close Project")

and then reopen it and you should be good to go.

Also if you want to make the `.deb` or the `.rpm` file you also need to install a couple extra things:

`sudo apt-get install binutils`

`sudo apt-get install fakeroot`

That's it! If you have any issues, please create an issue in Github and i'll get right on it.
