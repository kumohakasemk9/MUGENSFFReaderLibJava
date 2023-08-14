# KumoSFFReader
This library provides function reading MUGEN sff imagearray   
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
Get Image center axis parameter
- int GetLinkState(int imgid)   
Get link destination index if the image is linked, -1 if not linked.
- boolean IsSharedPalette(int imgid)   
Returns true if image is stored as 'shared palette' mode.  
in this mode, the image is sharing palette data with index0 image,  
even if image itself has palette data.  
  
Data getter:  
- byte\[\] GetRawImage(int imgid)   
Returns raw image (in sffv1, it is raw pcx) of the image at index imgid
  
Searchers:   
- int FindIndexByNumbers(int grp, int ino)   
Find image having the same Group Number as grp and the same Image Number as ino
then returns index, -1 if not found.
- int ListIndexesByGroupNo(int grp)   
Find images having the same group number as grp,
then returns indexes. Returning array will be sorted by index, not by Image Number.
  
Other functions:  
- closeSFF()   
Call it when application exit. You can cache all image data and call it too.
(sff file is closed but you can still get sff information, but you can not do
GetRawImage() )
- GetLibVersion()   
Returns version string
- GetLibInformation()   
Returns Library information string

You can generate javadoc         

# License
KumoSFFReader (C) 2023 Kumohakase    
CC BY-SA https://creativecommons.org/licenses/by-sa/4.0/    
Please consider supporting me https://ko-fi.com/kumohakase  

# Todo
- SFFv2 reading
- PCX decoding
