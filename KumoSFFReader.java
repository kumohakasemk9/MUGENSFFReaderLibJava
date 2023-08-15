package kumotechmadlab.sffreader;

import java.io.*;
import java.awt.image.*;
import java.awt.*;
import kumotechmadlab.sffreader.*;

public class KumoSFFReader {
	RandomAccessFile sff;
	SFFElements sffinfo[];
	int SharedPal[] = null;
	final static String VER = "Build-AUG152023";
	final byte IDENT[] = {'E', 'l', 'e', 'c', 'b', 'y', 't', 'e', 'S', 'p', 'r', 0};
	final byte VER_V1[] = {0, 1, 0, 1};
	
	/**
		Open SFF and cache some basic information
		@param filename SFF filename to open
		@throws IOException when IO Error occured
		@throws SFFDecodeException when SFF was bad
		@throws EOFException when SFF is shorter than expected
	*/
	public KumoSFFReader(String filename) throws IOException, SFFDecodeException {
		sff = new RandomAccessFile(filename, "r"); //open as binary read
		initSFF();
	}
	
	/**
		KumoSFFReader() but accepts java.io.File for SFF file pointer.
	*/
	public KumoSFFReader(File target) throws IOException, SFFDecodeException {
		sff = new RandomAccessFile(target, "r"); //open as binary read
		initSFF();
	}
	
	void initSFF() throws SFFDecodeException, IOException {
		//Read main header (top 28 bytes)
		byte hdr[] = new byte[28];
		sff.readFully(hdr);
		//Check magic byte, if wrong then error
		if(!Common.compareBytes(hdr, 0, IDENT) ) {
			throw new SFFDecodeException("File magic byte missmatch!", 
				SFFDecodeException.WRONG_IDENT);
		}
		//Check file ver
		if(!Common.compareBytes(hdr, 12, VER_V1) ) {
			throw new SFFDecodeException("Incompatible sff version!",
				SFFDecodeException.BAD_VER);
		}
		int image_total = (int)Common.b2ui(hdr, 20, 4); //Offset +20, uint32_t, total image count
		int file_offset = (int)Common.b2ui(hdr, 24, 4); //Offset +24, uint32_t, subfile offset	
		//Check records.
		if(!Common.in_range(image_total, 0, 65535) ||
			!Common.in_range(file_offset, 0, sff.length() ) ) {
			throw new SFFDecodeException("Bad header records!", 
				SFFDecodeException.BAD_FILE);
		}
		//Read basic information about each image in sff
		sffinfo = new SFFElements[image_total];
		for(int i = 0; i < image_total; i++) {
			sff.seek(file_offset); //seek to next subfile entry
			//read subfile header
			byte shdr[] = new byte[19];
			sff.readFully(shdr);
			int next_off = (int)Common.b2ui(shdr, 0, 4); //Offset: 0 uint32_t next data offset
			int file_len = (int)Common.b2ui(shdr, 4, 4); //Offset: 4 uint32_t image data length
			int imgx = Common.b2i16(shdr, 8); //Offset: 8 int16_t image center x
			int imgy = Common.b2i16(shdr, 10); //Offset: 10 int16_t image center y
			int grp = (int)Common.b2ui(shdr, 12, 2); //Offset 12 uint16_t image group number
			int ino = (int)Common.b2ui(shdr, 14, 2); //Offset 14 uint16_t image number
			//Offset 16 uint16_t image link destination id, if filelen = 0 this subfile is linked
			//to another image
			int ilin = (int)Common.b2ui(shdr, 16, 2);
			int pal = (int)Common.b2ui(shdr, 18, 1); //Offset 18 uint8_t Palette mode 1 or 0
			//Check parameters
			if(!Common.in_range(next_off, 0, sff.length() ) ||
				!Common.in_range(file_len, 0, sff.length() ) ||
				!Common.in_range(ilin, 0, image_total) ||
				!Common.in_range(pal, 0, 1) ) {
				throw new SFFDecodeException(String.format("Bad subheader records on index%d!", i) ,
					SFFDecodeException.BAD_FILE, i);
			}
			//link number must not point record itself.
			if(ilin == i && file_len == 0) {
				throw new SFFDecodeException(
					String.format("Subfile %d is linked image and pointing itself!", i),
					SFFDecodeException.BAD_FILE, i);
			}
			//Store parameters
			SFFElements e = new SFFElements();
			e.image_offset = file_offset + 0x20; //image offset = current subheader offset + 0x20
			e.image_length = file_len;
			e.x = imgx;
			e.y = imgy;
			e.groupid = grp;
			e.imageid = ino;
			e.linkid = ilin;
			if(pal == 0) { e.shared = false; } else { e.shared = true; }
			sffinfo[i] = e;
			file_offset = next_off; //advance file pointer for next entry
		}
		//Get Shared Palette
		//We have a chance for getting null in SharedPal, but then ConvertImage() throws Exception and
		//won't return malformed image probably...
		if(GetImageCount() != 0) {
			SharedPal = GetPalette(0);
		}	
	}
	
	/**
		Closes SFF. After closing sff, you can not run functions that throws IOException
		because big data (like actual image data) is not stored in your memory.
	*/
	public void closeSFF() {
		try { sff.close(); } catch (IOException ex) {}
	}

	/**
		Returns image count contained in the sff.
		@return total image count in sff
	*/
	public int GetImageCount() {
		return sffinfo.length;
	}
	
	/**
		Returns Group Number of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return Group Number of image
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int GetGroupNumber(int imgid) {
		return sffinfo[imgid].groupid;
	}
	
	/**
		Returns Image Number of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return Image number of image
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int GetImageNumber(int imgid) {
		return sffinfo[imgid].imageid;
	}
	
	/**
		Returns coordinate of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return specified center coordinate of image
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public Point GetCoordinate(int imgid) {
		SFFElements e = sffinfo[imgid];
		return new Point(e.x, e.y);
	}
	
	/**
		Returns x coordinate of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return specified X center coordinate of image
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int GetX(int imgid) {
		return sffinfo[imgid].x;
	}
	
	/**
		Returns y coordinate of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return specified Y center coordinate of image
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int GetY(int imgid) {
		return sffinfo[imgid].y;
	}
	
	/**
		Returns link id of image at specified index. Returns -1 if the image is not linked one.
		@param imgid Image index in order of sff subfiles.
		@return if image is linked, destination image index. or -1 if not linked.
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int GetLinkState(int imgid) {
		SFFElements e = sffinfo[imgid];
		if(e.image_length == 0) {
			return e.linkid;
		}
		return -1;
	}
	
	/**
		Returns image mode of image at specified index.
		@param imgid Image index in order of sff subfiles.
		@return Shared palette or not.
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public boolean IsSharedPalette(int imgid) {
		return sffinfo[imgid].shared;
	}
	
	/**
		Returns raw image data at specified index.
		@param imgid Image index in order of sff subfiles.
		@return Raw image bytearray. Maybe without palette data if shared palette.
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
		@throws IOException when IO Error occured.
		@throws EOFException when SFF is shorter than excepted.
	*/
	public byte[] GetRawImage(int imgid) throws IOException {
		SFFElements e = sffinfo[imgid];
		byte r[];
		int linkstate = GetLinkState(imgid);
		if(linkstate != -1) {
			//linked mode.
			return GetRawImage(linkstate); //Recursive call
		} else {
			//actual image. read data.
			r = new byte[e.image_length];
			sff.seek(e.image_offset);
			sff.readFully(r);
		}
		return r;
	}
	
	/**
		Returns palette data of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return 256 color palette int[], ARGB8888.
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
		@throws IOException when IO Error occured.
	*/
	public int[] GetPalette(int imgid) throws IOException {
		byte data[] = GetRawImage(imgid);
		//If PCX is shorter than header + palette length, maybe no palette
		if(data.length < 128 + 769) {
			return null;
		}
		//If palette data is not starting with uint8_t const 12, maybe there is no palette
		int paloff = data.length - 769;
		if(data[paloff] != 12) {
			return null;
		}
		//Decode palette data
		int pal[] = new int[256];
		//Decode palette, palette is 256 count array of RGB888 values 
		for(int i = 0; i < 256; i++) {
			int r = (int)Common.b2ui(data, i * 3 + paloff + 1, 1);
			int g = (int)Common.b2ui(data, i * 3 + paloff + 2, 1);
			int b = (int)Common.b2ui(data, i * 3 + paloff + 3, 1);
			//convert to ARGB8888
			//index 0 colour is always transparent, alpha=255 except index 0
			if(i != 0) {pal[i] += 0xff000000;}
			pal[i] += (r << 16) + (g << 8) + b;
		}
		return pal;
	}
	
	/**
		Get image data of specified index then convert it to BufferedImage
		@param imgid Image index in order of sff subfiles.
		@return BufferedImage, formatted in TYPE_INT_ARGB32.
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
		@throws IOException when IO Error occured.
		@throws EOFException when image data is shorter than excepted.
		@throws SFFDecodeException when palette data is missing but needed or when pcx file format is
											not suitable for MUGEN.
	*/
	public BufferedImage ConvertImage(int imgid) throws IOException, SFFDecodeException {
		byte data[] = GetRawImage(imgid);
		int pal[];
		if(data.length < 128) {
			throw new EOFException(
				String.format("index%d: PCX data is shorter than expected.", imgid) );
		}
		/*
			offset 0 uint8_t magic number should be 10
			offset 1 uint8_t pcx version must be 5 in MUGEN
			offset 2 uint8_t encoding must be 1 (RLE)
			offset 3 uint8_t bits per pixel must be 8 in MUGEN (256 indexed colour)
			offset 65 uint8_t number of colour plane must be 1 (256 indexed colour)
		*/
		if(data[0] != 10 || data[1] != 5 || data[2] != 1 || data[3] != 8 || data[65] != 1) {
			throw new SFFDecodeException(
				String.format("index%d: PCX data is not suitable for MUGEN", imgid),
				SFFDecodeException.BAD_SUBFILE, imgid);
		}
		//Calculate image size
		int minx = (int)Common.b2ui(data, 4, 2); //offset 4 uint16_t minx
		int miny = (int)Common.b2ui(data, 6, 2); //offset 6 uint16_t miny
		int maxx = (int)Common.b2ui(data, 8, 2); //offset 8 uint16_t maxx
		int maxy = (int)Common.b2ui(data, 10, 2); //offset 10 uint16_t maxy
		int imgwidth = maxx - minx + 1;
		int imgheight = maxy - miny + 1;
		//offset 66 uint16_t single scanline size per plane
		int scanline = (int)Common.b2ui(data, 66, 2);
		//Decode palette if image is in individual palette
		if(!IsSharedPalette(imgid)) {
			pal = GetPalette(imgid); //Get palette in PCX
		} else {
			pal = SharedPal; //Use shared palette
		}
		//Decode RLE and convert to BufferedImage
		BufferedImage r = new BufferedImage(imgwidth, imgheight, BufferedImage.TYPE_INT_ARGB);
		int runlen = -1;
		int currentx = 0;
		int currenty = 0;
		for(int i = 0;i < data.length - 128; i++) {
			int e = Byte.toUnsignedInt(data[i + 128]);
			if(e < 0xc0 || runlen != -1) {
				//pattern was not 0b11xxxxxx or previous octet was runlength
				//assume runlength is 1 when previous octet was not runlength
				if(runlen == -1) { runlen = 1; }
				//add octet for runlength times
				for(int j = 0; j < runlen; j++) {
					//store pixel data while currentx is less than image width
					if(currentx < imgwidth) {
						r.setRGB(currentx, currenty, pal[e]);
					}
					//advance currentx, if it is greater or equal than scanline,
					//return it to 0. we can simplly use ++ because BufferedImage and pcx
					//has the same coordinate system
					currentx++;
					if(currentx >= scanline) {
						currentx = 0;
						//advance y, if it exceeds imgheight, img is ready
						currenty++;
						if(currenty >= imgheight) { break; }
					}
				}
				if(currenty >= imgheight) { break; } //break outer loop is image is ready
				runlen = -1;
			} else {
				//pattern 0b11xxxxxx or previous octet was not runlength
				//set runlength
				runlen = e - 0xc0;
			}
		}
		return r;
	}
	
	/**
		Find image index by group number and image number
		Returns -1 when not found. if not -1, it is image index that
		has specified group number and image number
		@param grp Group Number
		@param ino Image number
		@return image index that has specified group and image numbers, -1 if not found
	*/
	public int FindIndexByNumbers(int grp, int ino) {
		for(int i = 0; i < GetImageCount(); i++) {
			SFFElements e = sffinfo[i];
			if(e.groupid == grp && e.imageid == ino) {
				return i;
			}
		}
		return -1;
	}
	
	/**
		Find image indexes by group number.
		@param grp Group Number
		@return image index array that has specified group number
	*/
	public int[] ListIndexesByGroupNo(int grp) {
		int r[] = new int[0];
		for(int i = 0;i < GetImageCount(); i++) {
			SFFElements e = sffinfo[i];
			if(e.groupid == grp) {
				int t[] = new int[r.length + 1];
				System.arraycopy(r, 0, t, 0, r.length);
				t[r.length] = i;
				r = t;
			}
		}
		return r;
	}
	/*
	//test
	public static void main(String args[]) throws Exception {
		//Open SFF
		KumoSFFReader sr = new KumoSFFReader("/mnt_hdd/owner/Desktop/mugen-1.1b1/chars/9/gy.sff");
		byte sp[] = new byte[769]; //Shared palette data
		//Process all images in sff
		for(int i = 0; i < sr.GetImageCount(); i++) {
			//Get Raw Image data of index i in sff
			byte d[] = sr.GetRawImageData(i);
			//Index0 image has shared palette data and get it
			if(i == 0) {
				if(d[d.length - 769] != 12) {
					throw new Exception("test fail: index0 does not contain pal.");
				}
				System.arraycopy(d, d.length - 769, sp, 0, 769);
			}
			//Include image information in filename
			int gid = sr.GetGroupNumber(i);
			int iid = sr.GetImageNumber(i);
			Point ip = sr.GetCoordinate(i);
			String fs = String.format("Image%d of %d %d, %d %dx%d", i,
							sr.GetImageCount() - 1, gid, iid, (int)ip.getX(), (int)ip.getY());
			//Output to file
			FileOutputStream f = new FileOutputStream(
											String.format("/mnt_hdd/owner/Desktop/output/%s.pcx", fs));
			//For shared palette image, delete existing palette (if contained),
			//then append index0 palette
			if(sr.IsSharedPalette(i)) {
				if(d[d.length - 769] == 12) {
					f.write(d, 0, d.length - 769);
				} else {
					f.write(d);
				}
				f.write(sp);
			} else {
				//If nonshared, output without process
				f.write(d);
			}
			System.out.println(i);
		}
		sr.closeSFF(); //close
	}
	*/
	
	/**
		Return library version string.
	*/
	public static String GetLibVersion() {
		return VER;
	}
	
	/**
		Returns library information string
	*/
	public static String GetLibInformation() {
		String s = "KumoSFFReader (C) 2023 Kumohakase\n" + 
						"CC BY-SA https://creativecommons.org/licenses/by-sa/4.0/\n" +
						VER + "\n" +
						"This library provides function to decode MUGEN SFF version 1\n" +
						"image array file from Elecbyte.\n" +
						"Please consider supporting me https://ko-fi.com/kumohakase.";
		return s;
	}
}
