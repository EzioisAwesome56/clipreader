# ClipReader
This is a project to port [this guy's python script](https://github.com/dobrokot/clip_to_psd) to java, which
in turn converts a Clip Studio Paint .clip file to a psd file. This is built upon that, but instead of converting to PSD,
converts directly to a java BufferedImage by either extracting the Preview Image from the internal SQLite database, or by
rebuilding the image from the raw layer data from the image itself.
## Usage
Add this as a library to your project. Then use any of the three exposed public functions that are supported
```java
com.eziosoft.clipreader.ClipReader.readClipFile(file){};
com.eziosoft.clipreader.ClipReader.readClipFilePreview(file){};
com.eziosoft.clipreader.ClipReader.readClipFileLayers(file){};
```
all the functions take one argument, an instance of a File that points to the .clip file you want to
open and get the contents of.<br>
readClipFile() will read the provided file, and reconstruct the image from all its layers to the best of its abilities. see "limitations" below<br><br>
readClipFilePreview() will read the provided file, and then extract the canvas preview from the SQLite database within the file, if it exists.
There is basically no documentation on the clip file format so I'm unsure if this will always be present<br><br>
readClipFileLayers() will read the provided file, and then return a simple array of BufferedImages, one for each layer in the file. This will also give you image masks from the file as well.

## Limitations
There are several small problems with this port, that I'm unaware of how to fix given the lack of .clip file documentation,
or any real pointers from the original python script itself. Said limitations include, but are not limited too:
- depending on the file, the colors will be decoded incorrectly. Some files seem to have their pixels decode in proper BGR format (Blue Green Red), while others seem to use BRG (Blue Red Green) for some reason. There is seemingly no way to tell them apart. The original python script appears to not suffer this
- The original example .clip files provided with the original python script do not correctly parse with this library. I don't know why, a lot of the chunks/layers seem to be missing offscreen attributes which are required for correct operation.
- Vector layers are not supported, but they where not supported in the original python script so this is not a new issue
- Text layers are also not supported, due to me not wanting to deal with porting that. Convert the text to raster layers will probably work though (if that is a thing at all)
- Monochrome layers will probably be very mangled. I could not find a way to determine what layer is a mask and what layer isn't besides assuming all monochrome layers are masks. This seems to work in testing, but your mileage will vary
- mipmap information is seemingly useless, it is not exported in any branches to PNG in the python script, and I wasn't about to reverse the photoshop format to do that myself
- Some layer blend effects are either missing or wrong; I tried my best to map them to a BlendComposition class I found online, but it's not perfect and some of them are not implemented, due to me not having test files that called for them