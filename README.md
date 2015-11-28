# FastBarcodeScanner
The FastBarcodeScanner package is a Java library letting you continuously take still pictures and search them for QR codes - without requiring any user involvment.

The entire process happpens in a background thread, without any user interface components (i.e. you will **not** see any camera window opening).

The library will only call back when it detects changes in what it sees - so until there's a QR code in view, your app will not be disturbed.

And did I mention that it's pretty fast? A sustained rate of 6-10 scans per second on a Nexus 5.

The package uses the Android Camera2 API for image capture, and was heavily inspired by the [Android Camera2Basic sample](https://github.com/googlesamples/android-Camera2Basic).

The package uses the brilliant [ZXing barcode library](https://github.com/zxing/zxing) for all the complicated stuff

##Using it

There are two ways of using this package: the demo app for verifying the speed and precision of the scanning, and the library for inclusin in your own app.

###The demo

The demo app source code is in the fast-barcode-scanner-demo directory, and the built APK file is in the release artifacts. Maybe some day I'll get around to uploading it to the play store, but until then you'll have to either build or install the APK directly.

The app is written to work a bit like a supermarket checkout scanner - you place your phone in a fixed location, the app then focuses the camera and starts looking for QR codes. Whenever it sees a new QR code it will vibrate and display the embedded text.

To use the demo,

- Start the app
- Place it in a fixed location, with a fixed distance to the scanning surface
- Press Focus, then Start
- Start moving QR codes past the camera, at a controlled speed

You should now hear/feel the phone vibrate whenever a QR code is recognized.

Is this a useful scenario? Well, it matched our needs pretty well (think mobile ticket scanning) - but it's only a demo, there's nothing in the library requiring this.

###The library

To include fast barcode scanning in you app, include the fast-barcode-scanner library in your app - if you're using gradle, here's the line to use:

    dependencies {
        ...
        compile 'dk.schaumburgit.fast-barcode-scanner:fast-barcode-scanner:1.0.2'
    }

Then, from you app, call `FastBarcodeScanner`:

    private void onClickFocus() {
        mScanner = new FastBarcodeScanner(this, null);
        mScanner.setBarcodeListener(this);
        mScanner.StartFocus();
    }
    
    private void onClickStart() {
        mScanner.StartScan();
    }
    
    @Override
    public void onBarcodeAvailable(final String barcode) {
        ...
    }


##Extending it

This is a fairly focused package (Android, post-Lollipop, QR codes only, etc.). Here are some thoughts on removing those restrictions - feel free to contribute code and suggestions.

###Supporting older Android versions
The package uses the new Android Camera2 API, which supports taking pictures without direct user involvement (i.e. there doesn't have to be an on-screen preview).

The old camera API does require a preview window - but you *can* allegedly cheat it by creating a 1x1 pixel preview window.

If that's correct, adding pre-Lollipop support to this package will be simple: create a StillSequenceCamera1 class next to the existing StillSequencecamera2, and use that for older versions

###Detecting other barcodes than QR
This will be embarrassingly simple - the ZXing library already supports them all.

It's just a question ofchanging a few lines in the TrackingBarcodeScanner class.

###Why the focus-then-scan approach?
Short answer: because that's the scenario I needed support for. And continuous focus adjustment reduces the image capture rate considerably.

But if you want something else, letting the focus-and-metering continue during capture is definitely poissble - lots of apps do it.

But the camera2 API *is* a complicated beast, so I left that for a future extension :-)

###Can it go faster?
Yes!

Currently, the limiting factor is the image capture rate - the ZXing library will take about 20ms to find and decode the QR code, and then the next 80-120 ms are spent waiting for the next image.

But instead of requesting image captures one-by-one, the Camera2 API supports continuous requests, which should give a much faster capture rate (I'm hoping for 20-30 ms)

So 30-50 captures per second appear within reach!

###Why not just use ZXing alone?
ZXing is a *great* library, and the ZXing app is great too.

But the recommended (or at least most-often-described) approach to using ZXing from you app uses Android "Intents" - which essentially start the ZXing app, gets it to show its UI (hiding your app in the process), capture a barcode, return it to your app, and exit (thereby showing your app again).

I got a sustained rate of 0.3-0.5 captures per second (yes, we're counting seconds-per-capture here, not captures-per-second) - and I found the user experience horrible. Sorry.

So I decided to use the less-often-described approach of doing my own image capture, and then passing the images to the ZXing core libraries, without any interprocess communication.

This package is the result of my efforts. Use it if you like, contribute if you can.

But ZXing is *great* - it's ZXing that's doing all the hard work.
