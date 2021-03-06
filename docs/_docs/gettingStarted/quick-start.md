---
permalink: /docs/gettingStarted/quick-start/
title: Quick Start
---

Get up and running with MDW in a hurry.

### Prerequisite
  - Java 8   
  This is the only hard requirement.  If you type `java -version` on the command line and see **1.8**, you're all set:
  ```
  java version "1.8.0_60"
  ...
  ```
  
### Install the MDW CLI
  The [MDW CLI](../cli) gives you commands for common operations like initializing your workspace and updating assets.
   - Download **mdw-cli.zip** from the latest MDW release on GitHub:<br>   
     <https://github.com/CenturyLinkCloud/mdw/releases>
   - Unzip anywhere on your hard drive.
   - Create an environment variable named MDW_HOME pointing to this location, and add its bin directory to your system PATH. 
  
### Initialize your workspace
  Now you can use the CLI to automatically create a new MDW workspace:
   - Open a command window in a directory that'll be the parent of your new workspace.
   - Use the `init` command to generate your workspace:
     ```
     mdw init my-mdw
     ```
  This sets up a basic MDW project structure using certain default values that [can be overridden](../cli).
  It also downloads some MDW base [assets](../../help/assets.html) to get you started.
  (You'll add your own assets on top of these as you build out your project.)
  The MDW base assets, along with your own, should be version controlled in Git.
  You can update these base assets at any time by running `mdw update`.
  
### Run MDW
  At this point you have a decision to make.  MDW comes with a rich set of REST service APIs, and also
  enables you to quickly [spin up your own](http://centurylinkcloud.github.io/mdw/docs/guides/MicroservicesCookbook/).  To host these services MDW relies on a 
  Java Servlet container.  Your options are:
  1. Use the self-contained [MDW Spring Boot jar](../spring-boot)
  2. Install [Apache Tomcat (or Jetty)](../SetupGuideForTomcat)
  
  Whichever option you choose, MDW behaves in exactly the same way.  To get you running quickly we'll start 
  with the Spring Boot option.  You can always switch to Tomcat later when you want to debug your assets,
  or if you need more control over your container.

#### Spring Boot Setup
  - Install the MDW binaries
    On the command line, cd into the project directory created by `mdw init`.  Then type
    ```
    mdw install
    ```
    This downloads the self-contained MDW Spring-Bootable jar file that matches mdwVersion in gradle.properties or pom.xml.  
    
### Command-line startup
  After installing, make sure you're in your project directory and type:   
  ```
  mdw run 
  ``` 

### Access MDWHub
  If everything is set up correctly, after MDW fully starts you should be able to access MDWHub in your browser:<br>
  <http://localhost:8080/mdw>
  
### Import into Eclipse
  Unless you suppressed Eclipse artifact generation (with the `--eclipse=false` option), your project is ready to be imported into
  Eclipse.  From the Eclipse File menu select > Import > General > Existing Projects into Workspace.  Then browse for your project
  directory.  To get started creating workflows and assets using MDW Designer in Eclipse, check out the
  [Designer User Guide](../../designer/user-guide).  