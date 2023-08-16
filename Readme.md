# KumoSFFReader
This library provides function reading MUGEN sff imagearray   
Now with sffv2 beta support (Raw data out only)   
file format made by Elecbyte  
Written in Java.   
   
# How to use
first, please add library jar to class path. and   

    import kumotechmadlab.sffreader.*;
    // ....
    KumoSFFReader sff = new KumoSFFReader(your_sff);
    // ....
   
KumoSFFReader functions:   
Constructors:   
- KumoSFFReader(String filename)   
Open sff having filename then open and cache information.
- KumoSFFReader(File target)   
Same but accepts java.io.File as file pointer
  
Information getter:   
- int GetImageCount()   
Get total image count in sff
- int GetGroupNumber(int imgid)   
Get Group Number parameter of yhe image at index imgid
- int GetImageNumber(int imgid)   
Get Image Number parameter
- java.awt.Point GetCoordinate(int imgid)
- int GetX(int imgid)
- int GetY(int imgid)    
Get Image center axis parameter
- int GetLinkState(int imgid)   
Get link destination index if the image is linked, -1 if not linked.   
- boolean IsSharedPalette(int imgid) SFFv1 only  
Returns true if image is stored as 'shared palette' mode.  
in this mode, the image is sharing palette data with index0 image,  
even if image itself has palette data.    
- int SFFv2GetImageColorDepth(int imgid) SFFv2 only    
Returns color depth of image at index imgid, usually 8 for 256 index color image   
- int SFFv2GetImageHeight(int imgid) SFFv2 only
- int SFFv2GetImageWidth(int imgid) SFFv2 only
- java.awt.Dimension SFFv2GetImageSize(int imgid) SFFv2 only  
Returns image size information of image   
- int SFFv2GetImageType(int imgid) SFFv2 only   
Returns image compression method.   
0: Raw 1: Invalid(Linked) 2: RLE8 3: RLE5 4: LZ5   
- int SFFv2GetImagePaletteIndex(int imgid) SFFv2 only  
Returns palette index associated with image    
- int SFFv2GetPaletteSize(int palid) SFFv2 only    
Returns palette color count      
- int SFFv2GetPaletteLinkedState(int palid) SFFv2 only  
Returns palette linked id if palette is linked, otherwise -1   

Data getter:  
- byte\[\] GetRawImage(int imgid)   
Returns raw image (in sffv1, it is raw pcx) of the image at index imgid
- BufferedImage ConvertImage(int imgid) SFFv1 only  
Returns java.awt.image.BufferedImage object of the image at index imgid   
- int\[\] GetPalette(int imgid) SFFv1 only  
Returns 256 colour palette (Array of ARGB8888 in order of colour index)    
 of the image at index imgid.  
- int\[\] SFFv2GetPalette(int palid) SFFv2 only  
Returns colour palette (array of ARGB888) of palette at index palid    
    
Searchers:   
- int FindIndexByNumbers(int grp, int ino)   
Find image having the same Group Number as grp and the same Image Number as ino   
then returns index, -1 if not found.
- int ListIndexesByGroupNo(int grp)   
Find images having the same group number as grp,   
then returns indexes. Returning array will be sorted by index, not by Image Number.  

Other functions:  
- GetSFFVersion()   
Returns 1 when reading sffv1, 2 when sffv2.  
- closeSFF()   
Call it when application exit. Buffering all image data and calling it is recommended.   
(sff file is closed but you can still get sff information, but you can not do   
Data getter Functions )   
- GetLibVersion()   
Returns version string
- GetLibInformation()   
Returns Library information string

SFFv2 only functions:   
These functions will return null, -1 or jiberrish when reder is reading sffv1   



You can generate javadoc         

# License
KumoSFFReader (C) 2023 Kumohakase    
CC BY-SA https://creativecommons.org/licenses/by-sa/4.0/    
Please consider supporting me https://ko-fi.com/kumohakase  

# Todo
- RLE8, RLE5, LZ5 decode
- Set external palette
