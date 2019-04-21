# The `FastBarcodeScanner` suite
FastBarcodeScanner is a suite of open-source components for barcode scanning on mobile platforms.
 
(diagram)

- **Android:** A set of Java libraries and a demo app:
    - `still-sequence-camera.aar`: Encapsulates the Android camera API, supplying a
    continuous stream of still images.
    - `tracking-barcode-scanner.aar`: Encapsulates the extraction of barcodes froma continuous 
    stream of images.
    - `fast-barcode-scanner.aar`: When requested by the application, it loads the other two 
    libraries, starts the image capture, 
    passing the stream of images to the barcode scanner. Any barcodes found are sent back to 
    the application.
    - `fast-barcode-scanner-demo.apk`: Simple demo app, showing the code in action.
- **iOS:**
- **Cordova/Phonegap plugin:**

## Advantages

The FastBarcodeScanner suite provides **fast**, **continuous**, **headless** scanning 
for barcodes, using the camera built into your phone or tablet.
 
The main advantages are:
 
- **Fast:** I've measured 30 barcodes scanned per second at a resolution of 2048x1536 (on a Pixel XL running Android 8.1) - though poor lighting, motion blur etc. will reduce this
- **Headless:** The library does not require any user interface whatsoever. It will grab images directly from the camera and analyse them without requiring any access to the user interface (caveat: Android versions prior to Lollipop require that you let the camera have 1 (one) pixel to play with - see details later)
- **Continuous:** Once you have called the start() method, the FastBarcodeScanner library will continue grabbing and analyzing images until you call stop (), with no interention required from you - it will just call you back whenever it finds a barcode.
- **Optimized:** To reduce the load on the application, FastBarcodeScanner uses configurable *filtering,* *optimistic tracking,* *event conflation* and *scan state debouncing*.
- **Open source**: the entire FastBarcodeScanner library is open source (as is the ZXing library, BTW) - so it's free for you to examine, tweak, optimize and fix
 
## Advanced configuration

### Filtering
This can be modified to look only for specific barcode types:

The scanner can also be configured to disregard any barcode whose content doesn't match a specified 
pattern:

### Debounce

### Event conflation

- **None:** No events of this kind are ever let through.
- **First:** Only the first event in a sequeence of this kind is let through. Note the 
             qualifier 'in a sequence': if a scanning session consists of a sequence
             of hits, a sequence of blanks and a sequence of hits, `ConflateHit(First)` 
             will mean that *2* hit events are let through - the first from the first 
             dequence and the first from the second sequence.
- **Changes:** An event of this kind is let through if it is the first, or if its 
               value has changed.
- **All:** Every event of this kind is let though (i.e. conflation is off)

### Optimistic tracking

When scanning at 5-30 fps as `fast-barcode-scanner` does, it's a pretty good bet that 
frame *n* will contain a barcode in roughly the same place as frame *(n-1).*
 
This assumption is the basis of the *optimistic tracking* feature.

Optimistic tracking will remember where it sucessfully finds a barcode, and will look in the 
same place (plus a configurable margin) the next time.
 
If the barcode is not found in the expected location, `fast-barcode-scanner` will revert
to looking in the entire frame.

After a configurable number of such failures, fbs will switch back out of tracking mode - 
until next time it acquires a barcode.

### Fixed-distance or variable-distance scanning
If the items you are scanning are always at a *fixed distance* from the camera, you only have to focus the camera once at the beginning of the scanning session. This gives much higher speed and better results.

Examples of fixed-distance scanning scenarios include ticket verification, document identification, library book scanners, some forms of inventory taking: all scenarios where you can mount the camera at a fixed location, and move the barcoded items past:

(photo)

If, on the other hand, you are scanning at *variable distances*, you have to refocus constantly - at the cost of lower performance (the camera can take as much as 2-3 seconds to regain focus).

Examples of variable-distance scanning include scanning fixed items (moving from item to item will ruin the focus lock).

FastBarcodeScanner caters to both scenarios: the property `LockFocus` determines whether the camera should lock its focus as soon as possible (`true`), or continuously attempt to refocus (`false`).

The focus when writing this library has been overwhelmingly on the fixed-distance sscenario - so I suspect there are considerable optimizations to be made in the focusing engine for the variable-distance scenario.

So feel free to suggest (or make) improvements!
 
### Fixed-distance scanning: further optimizations

When scanning the captured images, FastBarcodeScanner uses a *tracking* approach described for the TrackingBarcodeScanner library: it first looks in the place where it previously found a barcode - only if that doesn't succeed does it look at the whole image.

Because the first scan is 3-5 times faster than the second, tracking loss is relatively expensive.

And when you are replacing one scanned item with the next, there's a brief interval with no barcode in sight - causing precisely this tracking loss!

Our fix for this has been to 

1. Construct the scanning cradle so barcodes are always in roughly the same location
2. Glue a dummy barcode to the scanning surface exactly where the real barcodes will be - this prevents tracking loss when replacing items

This naturally assumes that all items being scanned are roughly identical, and have (roughly) identically placed barcodes. You can adjust the definition of "roughly identical" with the `RelativeTrackingMargin` property.

