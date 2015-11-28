#Tracking barcode scanner

The tracking-barcode-scanner Java library uses the ZXing core library to find QR codes in images.

It adds the following to the core ZXing offerings:

* **Pre-processing:** It preprocesses standard image formats (currently JPEG and YUV, as supplied by all cameras) into the internal format expected by ZXing.
* **Tracking:** It speeds up scanning by first looking in the part of the image where it last found a barcode.

If tracking is succesfull, it will reduce scanning time from 80-100ms to 15-20ms (1024x768 image, on a Nexus5).

The effect of tracking naturally relies on the images being part of a closely spaced sequence, so a barcode has not moved far between two images

But for controlled movements, tracking will be succesfull in 80% of the cases (in my tests: stationary camera, barcodes moved past at a controlled pace).

And the tracking tolerance - i.e. the relative margin that is added to the previous match to get the reduced search area - is adjustable, allowing for lower or higher speeds.
