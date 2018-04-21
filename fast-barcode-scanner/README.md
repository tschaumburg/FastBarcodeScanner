# The `fast-barcode-scanner` Java library
 
## Getting started

In your `build.gradle`,, import `fast-barcode-scanner`:

```groovy
dependencies {
    implementation 'dk.schaumburgit.fast-barcode-scanner:fast-barcode-scanner:2.0.0.alpha5'
}
```

In your `Application` class, add code to [build a scanner intance,](#Build-a-scanner-instance) [start 
scanning,](#start-scanning) [stop scanning](#stop-scanning) and finally [close the scanner instance](#close-the-scanner-instance):

```java
public class ExampleApplication
   extends AppCompatActivity 
   implements BarcodeDetectedListener
{
   IBarcodeScanner mScanner = null;
   
   @Override public void onCreate() 
   {
      super.onCreate();
      this.requestPermissions(
         new String[]{Manifest.permission.CAMERA},
         REQUEST_CAMERA_PERMISSION
      );
      
      // Build a scanner instance:
      mScanner = BarcodeScannerFactory.builder().build(this);
      
      // ...set up buttons to call startScan() and stopScan()...
   }
   
   private void startScan() 
   {
      // Start scanning:
      mScanner.StartScan(this);
   }
   
   private void stopScan() 
   {
      // Stop scanning:
      mScanner.StopScan();
   }
   
   @Override
   public void OnHit(
      BarcodeInfo barcodeInfo,
      byte[] image, 
      int format, 
      int width, 
      int height
   )
   {
      // ...process the barcode in barcodeInfo.barcode...
   }
   
   @Override
   public void OnBlank() 
   {
      //...process blank (no-hit) scans...
   }
      
   @Override
   public void OnError(Exception error) 
   {
      //...process scan error...
   }
   
    @Override
    protected void onDestroy() 
    {
        // Close the scanner instance
        mScanner.Close();
        super.onDestroy();
    }
}
```

##Basic use
Any application using the `fast-barcode-scanner` library will use these four steps: building a 
scanner, starting it, stopping it and finally closing it to free any resources.

###Build a scanner instance
Building a scanner instance uses the [builder pattern.](https://en.wikipedia.org/wiki/Builder_pattern)
In its simplest form building a scanner instance looks like this:
```
mScanner =
   BarcodeScannerFactory
   .builder()
   .build();
```

The newly built scanner instance hasn't started scanning anything yet - but it has set up 
its image processing pipeline, and reserved the necessary resources.

###Start scanning

Tu start actually scanning for barcodes, call the `StartScan` method, specifying where 
the scanner should send any detection events:
```
mScanner.StartScan(
   new BarcodeListener() 
   {
      @Override
      public void OnHit(BarcodeInfo barcodeInfo, byte[] image, int format, int width, int height) 
      {
         // ...process the barcode in barcodeInfo.barcode...
      }
      @Override
      public void OnBlank() 
      {
         //...process blank (no-hit) scans...
      }
      @Override
      public void OnError(Exception error) 
      {
         //...process scan error...
      }
   }
);
```
From this point, the scanner instance will continuously scan the video feed from the back camera
for barcodes, calling `OnHit`, `OnBlank` and `OnError` as appropriate.

###Stop scanning
To stop the continuous scanning, call the `StopScan` method
```
mScanner.StopScan();
```
Notice that images that are already in the pipeline when `StopScan` is called will continue
being processed - so it may taken some milliseconds for the events to stop.
###Close the scanner instance
Calling `Close` will dispose of any resources held by the scanner (including releasing the camera for others to use):
```
mScanner.Close();
```

##Advanced configuration
The [Basic use] section showed how to build a scanner instance with the default configuration:
```
mScanner = BarcodeFactory.builder().build();
```
This section will show how scanners with custom configurations can be built.

###Filtering
In the default configuration, `fast-barcode-scanner` scans for *any* (supported) barcode format,
with *any* contents.

This can be modified to look only for specific barcode types:
```
mScanner =
   BarcodeScannerFactory
   .builder()
   .FindQR()
   .build();
```
Note that looking for a single barcode type is the fastest.

The scanner can also be configured to disregard any barcode whose content doesn't match a specified 
pattern:
```
mScanner =
   BarcodeScannerFactory
   .builder()
   .BeginsWith("test:")
   .build();
```

###Preview
When using the default configuration, the scanner runs in *headless* mode - i.e. without showing 
the video feed on-screen.

This can be changed my passing a `TextureView` to the builder:
``` 
TextureView myPreview = (TextureView)findViewById(R.id.preview);
mScanner =
   BarcodeScannerFactory
   .builder(myPreview)
   .build();
```
Note that this is only supported on Android version **Lollipop** (aka **version 5.0** aka 
**API level 21**) or later. See the 
[Pre-Lollipop support](#pre-lollipop-support) section.

###Pre-Lollipop support
Android version **Lollipop** (aka **version 5.0** aka **API level 21**, released November 2014)
introduced a new, considerably faster and more capable camera API called *Camera2*.

`fast-barcode-scanner` supports both legacy, pre-Lollipop platforms and modern Camera2 platforms.

If you require headless scanning, `fast-barcode-scanner` will handle the platform detection for you:


If you want an on-screen preview, things get a little more complicated because the two APIs use
different UI components for preview - `SurfaceView` (pre-Lollipop) and `TextureView` (Camera2).

As a result, `fast-barcode-scanner` has two differen ways to create a builder with preview - one for pre-Lollipop 
and one for Camera2 - and a helper method telling which is supported

You will then have to add *both* a `SurfaceView` and a `TextureView` to your UI, and use 
each as appropriate:

```
switch (BarcodeFactory.SupportedAPI)
{
   case SupportedCameraAPI.Camera2:
      TextureView preview = ...create or get...
      mScanner = 
         BarcodeFactory
         .builder(preview)
         .build();
      break;

   case SupportedCameraAPI.LegacyOnly:
      SurfaceView legacyPreview = ...create or get...
      mScanner = 
         BarcodeFactory
         .builderLegacy(legacyPreview)
         .build();
      break;
 }
```
After the cration of the builder, all other features work exactly the same on both 
platforms - albeit much slower on pre-Lollipop.

###Debounce

In a perfect world, scanning results in a  perfect sequence of correct results.
But in reality, *noise* intrudes - spurious blank or error readings, some times 
making up as much as 10-20% of the readings.

The common cure is to introduce a *debouncing* filter: a change in state (hit, 
blank, error) is only accepted after remaining consistent for a configurable 
number of scans.

``` 
mScanner =
   BarcodeScannerFactory
   .builder()
   .debounceBlanks(2)
   .debounceErrors(2)
   .build();
```

Note that there is no `DebounceHits` - there are so many error checks involved in
barcode detection tha fale hits are virtually non-existing.

###Event conflation

Without *event conflation*, `fast-barcode-scanner`  would emit an event at each scan - up to 30 times per second -
regardless if anything has changed.

For applications not needing this level of detail, event conflation can be configured for each
type of event (hit, blank or error):

``` 
mScanner =
   BarcodeScannerFactory
   .builder()
   .conflateHits(EventConflation.Changes)
   .conflateBlanks(EventConflation.First)
   .conflateErrors(EventConflation.None)
   .build();
```

Conflation can be set to 

- **None:** No events of this kind are ever let through.
- **First:** Only the first event in a sequeence of this kind is let through. Note the 
             qualifier 'in a sequence': if a scanning session consists of a sequence
             of hits, a sequence of blanks and a sequence of hits, `ConflateHit(First)` 
             will mean that *2* hit events are let through - the first from the first 
             dequence and the first from the second sequence.
- **Changes:** An event of this kind is let through if it is the first, or if its 
               value has changed.
- **All:** Every event of this kind is let though (i.e. conflation is off)

###Optimistic tracking

When scanning at 5-30 fps as `fast-barcode-scanner` does, it's a pretty good bet that 
frame *n* will contain a barcode in roughly the same place as frame *(n-1).*
 
This assumption is the basis of the *optimistic tracking* feature.

Optimistic tracking will remember where it sucessfully finds a barcode, and will look in the 
same place (plus a configurable margin) the next time.
 
If the barcode is not found in the expected location, `fast-barcode-scanner` will revert
to looking in the entire frame.

After a configurable number of such failures, fbs will switch back out of tracking mode - 
until next time it acquires a barcode.


**Note:** Optimistic tracking is only implemented for single-barcode scanning.

**Note:** Optimistic tracking is a performance-boosting feature - it is not intended 
to alter the result of the scans








##Performance considerations 
Many things affect the performance of FastBarcodeScanner - these are the most important:

###Android version: Camera or Camera2
This is simple: if you use the FastBarcodeScanner on Android Lollipop or later, you get access to the much more efficient Camera2 API. The effect is a factor 5x speedup.

The cost is that you cut yourself off from 17.7% of the installed base (as of February 
2018 - check [Android Dashboard](#https://developer.android.com/about/dashboards/index.html)) 
for the latest numbers.

To have the best of both worlds, write your code to test for the precense of Camera2, and use the proper constructor if available:

```
switch (BarcodeFactory.SupportedAPI)
{
   case SupportedCameraAPI.Camera2:
      TextureView preview = ...create or get...
      mScanner = 
         BarcodeFactory
         .builder(preview)
         .build();
      break;

   case SupportedCameraAPI.LegacyOnly:
      SurfaceView legacyPreview = ...create or get...
      mScanner = 
         BarcodeFactory
         .builderLegacy(legacyPreview)
         .build();
      break;
 }
```

###Fixed-distance or variable-distance scanning
If the items you are scanning are always at a *fixed distance* from the camera, you only have to focus the camera once at the beginning of the scanning session. This gives much higher speed and better results.

Examples of fixed-distance scanning scenarios include ticket verification, document identification, library book scanners, some forms of inventory taking: all scenarios where you can mount the camera at a fixed location, and move the barcoded items past:

(photo)

If, on the other hand, you are scanning at *variable distances*, you have to refocus constantly - at the cost of lower performance (the camera can take as much as 2-3 seconds to regain focus).

Examples of variable-distance scanning include scanning fixed items (moving from item to item will ruin the focus lock).

FastBarcodeScanner caters to both scenarios: the property `LockFocus` determines whether the camera should lock its focus as soon as possible (`true`), or continuously attempt to refocus (`false`).

The focus when writing this library has been overwhelmingly on the fixed-distance sscenario - so I suspect there are considerable optimizations to be made in the focusing engine for the variable-distance scenario.

So feel free to suggest (or make) improvements!
 
###Fixed-distance scanning: further optimizations

When scanning the captured images, FastBarcodeScanner uses a *tracking* approach described for the TrackingBarcodeScanner library: it first looks in the place where it previously found a barcode - only if that doesn't succeed does it look at the whole image.

Because the first scan is 3-5 times faster than the second, tracking loss is relatively expensive.

And when you are replacing one scanned item with the next, there's a brief interval with no barcode in sight - causing precisely this tracking loss!

Our fix for this has been to 

1. Construct the scanning cradle so barcodes are always in roughly the same location
2. Glue a dummy barcode to the scanning surface exactly where the real barcodes will be - this prevents tracking loss when replacing items

This naturally assumes that all items being scanned are roughly identical, and have (roughly) identically placed barcodes. You can adjust the definition of "roughly identical" with the `RelativeTrackingMargin` property.

## Q&amp;A


**Update Feb 2018:** Using a 2016 Pixel XL running Android 8.1 achieves a scan rate of 29.6 scans
per second at a requested resolution of 2048x1536!

Although this was in optimum conditions (stationary target, good lighting, no glare or motion 
blur), it shows what has been achievable since November 2016 - and will be commonplace 
very soon, thanks to Moore's law.

(Note: this was a very informal test, using a wrist watch, so don't rely on my numbers - 
make your own measurements)

###Can it go faster?
Yes! - as camera and general platform performance improves, `fast-barcode-scanner` will 
improve too.

But it has always been my goal to achieve realtime, full-image scanning at HD video 
levels - and with 30fps@2048x1535 achieved, this has pretty much been achieved for 
high-end phones.

So now my personal goal has changed "realtime scanning at HD video levels - **using
 my mothers phone**" - i.e. more a question of broadening the performance on the 
 average-to-lowend phones.
 
But if you disagree, feel free to suggest (or even better: implement) improvements 
to peak performance!

###Why not just use ZXing alone?
ZXing is a *great* library, and the ZXing app is great too.

But the recommended (or at least most-often-described) approach to using ZXing from you app uses Android "Intents" - which essentially start the ZXing app, gets it to show its UI (hiding your app in the process), capture a barcode, return it to your app, and exit (thereby showing your app again).

I got a sustained rate of 0.3-0.5 captures per second (yes, we're counting seconds-per-capture here, not captures-per-second) - and I found the user experience horrible. Sorry.

So I decided to use the less-often-described approach of doing my own image capture, and then passing the images to the ZXing core libraries, without any interprocess communication.

This package is the result of my efforts. Use it if you like, contribute if you can.

But ZXing is *great* - it's ZXing that's doing all the hard work.
