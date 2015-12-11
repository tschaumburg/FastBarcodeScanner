# FastBarcodeScanner
The FastBarcodeScanner is an **open-source** **library** providing **fast**, **continuous**, **headless** scanning for barcodes, using the camera built into your Android phone or tablet, combined with the amazing **ZXing** barcode library.
 
The main advantages are:
 
- **Library:** The core product of this project is the fastbarcodescanner.aar library which is straight-forward to integrate
in your app. More details below!

- **Headless:** The library does not require any user interface whatsoever. It will grab images directly from the camera and analyse them without requiring any access to the user interface (caveat: Android versions prior to Lollipop require that you let the camera have 1 (one) pixel to play with - see details later)

- **Continuous:** Once you have called the start() method, the FastBarcodeScanner library will continue grabbing and analyzing images until you call stop (), with no interention required from you - it will just call you back whenever it finds a barcode.

- **Fast:** I've measured 6-10 barcodes scanned per second - though poor lighting, motion blur etc. will reduce this

- **ZXing:** all the complicated bacode recognition is provided by ZXing, the industry-standard open source barcode library. All credit goes to the ZXing team - this library only adds camera handling.

- **Open source**: the entire FastBarcodeScanner library is open source (as is the ZXing library, BTW) - so it's free for you to examine, tweak, optimize and fix
 
Further points of potential interest:
 
- **Cordova/Phonegap plugin:** There's a Cordova/Phonegap plugin for the FastBarcodeScanner library available at https://github.com/tschaumburg/FastBarcodeScannerPlugin

- **Demo app:** In the FastBarcodeScanner repo, there's a proof-of-concept demo app. It contains a start button, a text field where any scanned barcodes are written, and a stop button - not exactly rocket science, but that's about as complicated as it gets. See [xxxxx](#the-demo-app)
 
##The demo app
The demo is written as a proof-of-concept for low-cost, highly portable item registration: you place your phone in a fixed location, and move each bar-coded item past the phone:

<photo>

If you're just trying this out, a simpler form of mounting will do:

<photo>

You simply start the demo app, mount the phone, and press start:

<screen shot>

You can now move barcoded items pas the phones camera, and the app will display any
the phone in your
If the barcode is recognized, the phone vibrates briefly. In the real world, the app would naturally do something with the scanned barcodes: upload them to an inventory server, identify duplicated event tickets, etc.
 
But this is just a demo app, so we'll keep it simple: there's a start and a stop button, and a field displaying the contents of the currently scanned barcode:
 
<screen shot)
 
You simply place the phone in your holder, press start, and 
 
##Using the fastbarcodescanner.aar library
 
The fastbarcodescanner.aar library has a very simple API:
 
1. Instantiate a FastBarcodeScanner using one of the following constructors (depending on the Android version you're running):

    *Android Lollipop (API level 21) or later:*

    ```
     FastBarcodeScanner fbs = new FastBarcodeScanner(activity); // scan without any on-screen preview
     FastBarcodeScanner fbs = new FastBarcodeScanner(activity, textureView); // scan with preview displayed in textureView
    ```
    *Earlier Android versions:*
    ```
     FastBarcodeScanner fbs = new FastBarcodeScanner(activity, surfaceView); // scan with preview displayed in surfaceView
    ```

2. Start scanning:

    ```
    fbs.StartScan(
       true,
       new BarcodeDetectedListener {
          @override
          onBarcodeAvailable(String barcode) {
             ...*(see below)*
          }
          @override
          onError(Exception error) {
             ...*(see below)*
          }
       },
       handler
    );
    ```
    
    The first parameter above determines if the camera should lock the focus when first achieved (`true`) or continue to refocus (`false`). See the section on [performance](performance-considerations) below
    
3. The code in `onBarcodeAvailable()` and `onError()` above will be called using the thread wrapped by the handler parameter. What you do with the barcode is up to you - the FastBarcodeScanner is already busily looking for the next barcode on its own, internal thread.

4. When you're done, simply call stopScan():

    ```
    fbs.stopScan();
    ```
    This will stop all the internal threads, and (most importantly) free the camera

5. A well-behaved app will implement onPause() and onResume() to call stopScan() and startScan(), thus freeing e.g. the camera for use by other apps. 

##Performance considerations 
Many things affect the performance of FastBarcodeScanner - these are the most important:

###Android version: Camera or Camera2
This is simple: if you use the FastBarcodeScanner on Android Lollipop or later, you get access to the much more efficient Camera2 API. The effect is a factor 5x speedup.

The cost is that you cut yourself off from 50-70% of the installed base (as of November 2015 - and dropping quickly).

To have the best of both worlds, write your code to test for the precense of Camera2, and use the proper constructor if available:

```
```

###Fixed-distance or variable-distance scanning
If the items you are scanning are always at a *fixed distance* from the camera you only have to focus the camera once at the beginning of the scanning session - this gives much higher speed and better results.

Examples of fixed-distance scanning scenarios include ticket verification, document identification, library book scanners, some forms of inventory taking: all scenarios where you can mount the camera at a fixed location, and move the barcoded items past:

(photo)

If, on the other hand, you are scanning at *variable distances*, you have to refocus constantly - at the cost of lower performance (the camera can take as much as 2-3 seconds to regain focus).

Examples of variable-distance scanning include scanning fixed items (moving from item to item will ruin the focus lock).

FastBarcodeScanner caters to both scenarios: the first parameter of `startScan()` determines whether the camera should lock its focus as soon as possible (`true`), or continuously attempt to refocus (`false`).

The focus when writing this library has been overwhelmingly on the fixed-distance sscenario - so I suspect there are considerable optimizations to be made in the focusing engine for the variable-distance scenario.

So feel free to suggest (or make) improvements!
 
###Fixed-distance scanning: further optimizations 
 
## Previous text

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
