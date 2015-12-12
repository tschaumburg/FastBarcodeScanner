https://github.com/blundell/release-android-library
I've been having a lot of practical problems publishing this project to JCenter/Maven - and I've managed to nearly forget the solution every time.

So this is mostly a note to myself.

##The problem
This project produces three `.aar` Java libraries and an Android `.apk` application, which depend on eachother like this:

    fast-barcode-scanner-demo.apk => fast-barcode-scanner.aar => still-sequence-camera.aar
                                                              => tracking-barcode-scanner.aar

The problem is that the build.gradle file for `fast-barcode-scanner` contains the following internal references:

    dependencies {
        ...
        compile project(':still-sequence-camera')
        compile project(':tracking-barcode-scanner')
    }

Let's say I want to release version 1.0.4. If I go ahead and do that, the first app referencing the new `fast-barcode-scanner-1.0.4.aar` will get compilation errors that the internal references to `still-sequence-camera.aar` and `tracking-barcode-scanner.aar` cannot be resolved.

So I update the `build.gradle` references to properly versioned refs:

    dependencies {
        ...
        compile 'dk.schaumburgit.fast-barcode-scanner:still-sequence-camera:1.0.4'
        compile 'dk.schaumburgit.fast-barcode-scanner:tracking-barcode-scanner:1.0.4'
    }

But now the `fast-barcode-scanner` project won't build because version 1.0.4 hasn't been released yet.

Catch-22: I naturally cannot release until I've built - but I cannot build until I've released!

The solution of course if to work by way up the dependency tree: first build and release the two independent projects in version 1.0.4, then update the references in the intermediate `fast-barcode-scanner`library and build-and-release that, and finally update-build-and-release the top-level `fast-barcode-scanner-demo` project

##Define the release
First, log into JCenter at https://bintray.com/ and create the new release in JCenter:

(screenshot)

Next, in each of the 4 gradle files, edit

    ext {
        ...
        PUBLISH_VERSION = '1.0.4'
    }

##Release the independents
Now release the two projects without dependencies, still-sequence-camera and tracking-barcode-scanner:

    cd still-sequence-camera
    ..\gradlew clean build generateRelease
    cd ..

    cd tracking-barcode-scanner
    ..\gradlew clean build generateRelease
    cd ..

This produces two zip files ready for upload:

    still-sequence-camera\build/release-1.0.4.zip
    tracking-barcode-scanner\build/release-1.0.4.zip

These can be uploaded by clicking "Upload files":

photo2a

Now, for each of the zip files, do the following (first all the steps for one, then all the steps for the other - trying to combine have given problems).

Drag the zip onto the download area:

photo 2b

Remember to click "Explode this file" before clicking "Save changes".

Then click "Publish":

photo3

##Release the library project
Now that the two libraries are available as properly versioned entities, the library project can be updated to reference them.

In the fast-barcode-scanner\build.gradle file, update the references from local project references to proper versioned references:

    dependencies {
        compile fileTree(include: ['*.jar'], dir: 'libs')
        testCompile 'junit:junit:4.12'
        compile 'dk.schaumburgit.fast-barcode-scanner:still-sequence-camera:1.0.4'
        compile 'dk.schaumburgit.fast-barcode-scanner:tracking-barcode-scanner:1.0.4'
        //compile project(':still-sequence-camera')
        //compile project(':tracking-barcode-scanner')
    }

Then build and package:

    cd fast-barcode-scanner
    ..\gradlew clean build generateRelease
    cd ..

Like before, this produces a zip-file ready for upload:

    fast-barcode-scanner\build\release-1.0.4.zip

Upload and publish like before.

##Release the demo

Though the demo is never referenced using a gradle reference, we'll publish it in JCenter too, just for completeness.

In `fast-barcode-scanner-demo\build.gradle`, update the reference to the fast-barcode-scanner library:

    dependencies {
        compile fileTree(include: ['*.jar'], dir: 'libs')
        testCompile 'junit:junit:4.12'
        compile 'com.android.support:appcompat-v7:23.1.0'
        compile 'com.android.support:design:23.1.0'
        compile 'com.android.support:support-v4:23.1.0'
        compile 'com.android.support:support-v13:23.1.0'
        compile 'dk.schaumburgit.fast-barcode-scanner:fast-barcode-scanner:1.0.4'
        -//compile project(':fast-barcode-scanner')-
    }

Build:

    cd fast-barcode-scanner-demo
    ..\gradlew clean build generateRelease
    cd ..

And then upload and publish.


