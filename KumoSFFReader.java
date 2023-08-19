package kumotechmadlab.sffreader;

import java.io.*;
import java.awt.image.*;
import java.awt.*;
import javax.imageio.*;
import kumotechmadlab.sffreader.*;

/** SFF Reader class */
public class KumoSFFReader {
	RandomAccessFile sff;
	SFFElements sffinfo[];
	SFFPaletteElement palinfo[];
	int SharedPal[] = null;
	final static String VER = "Build-AUG162023";
	final byte IDENT[] = {'E', 'l', 'e', 'c', 'b', 'y', 't', 'e', 'S', 'p', 'r', 0};
	final byte VER_V1[] = {0, 1, 0, 1};
	final byte VER_V2[] = {0, 0, 0, 2};
	final byte VER_V2_1[] = {0, 1, 0, 2};
	int SFFVersion;
	/** SFF version 1 */
	public static final int SFF_V1 = 1; 
	/** SFF version 2 */
	public static final int SFF_V2 = 2; 
	/** Raw format image */
	public static final int SFFV2_IMGTYPE_RAW = 0; 
	/** Invalid format image (mostly linked) */
	public static final int SFFV2_IMGTYPE_INVALID = 1; 
	/** RLE8 format image */
	public static final int SFFV2_IMGTYPE_RLE8 = 2; 
	/** RLE5 format image */
	public static final int SFFV2_IMGTYPE_RLE5 = 3; 
	/** LZ5 format image */
	public static final int SFFV2_IMGTYPE_LZ5 = 4; 
	
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
		//Read magic byte and version
		byte hdr[] = new byte[16];
		sff.readFully(hdr);
		//Check magic byte, if wrong then error
		if(!Common.compareBytes(hdr, 0, IDENT) ) {
			throw new SFFDecodeException("File magic byte missmatch!", SFFDecodeException.WRONG_IDENT);
		}
		//Check file ver
		if(Common.compareBytes(hdr, 12, VER_V1) ) {
			initSFFv1(); //SFF v1 reading
		} else if(Common.compareBytes(hdr, 12, VER_V2) || Common.compareBytes(hdr, 12, VER_V2_1) ) {
			initSFFv2(); //SFF v2 reading
		} else {
			throw new SFFDecodeException("Incompatible sff version!", SFFDecodeException.BAD_VER);
		}
	}
	
	void initSFFv1() throws SFFDecodeException, IOException {
		SFFVersion = SFF_V1;
		//Read header except magic bytes and version info
		byte hdr[] = new byte[20];
		sff.readFully(hdr);
		int image_total = (int)Common.b2ui(hdr, 4, 4); //Offset +20, uint32_t, total image count
		int file_offset = (int)Common.b2ui(hdr, 8, 4); //Offset +24, uint32_t, subfile offset	
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
				!Common.in_range(ilin, 0, image_total) || !Common.in_range(pal, 0, 1) ) {
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
			e.imgoffset = file_offset + 0x20; //image offset = current subheader offset + 0x20
			e.imglength = file_len;
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
	
	void initSFFv2() throws SFFDecodeException, IOException {
		SFFVersion = SFF_V2;
		//Read header except magic bytes and version info
		byte hdr[] = new byte[52];
		sff.readFully(hdr);
		int spr_offset = (int)Common.b2ui(hdr, 20, 4); //offset 36 uint32_t sprite data table offset
		int spr_count = (int)Common.b2ui(hdr, 24, 4); //offset 40 uint32_t sprite data count
		int pal_offset = (int)Common.b2ui(hdr, 28, 4); //Offset 44 uint32_t palette data table offset
		int pal_count = (int)Common.b2ui(hdr, 32, 4); //offset 48 uint32_t palette data count
		int ldata_offset = (int)Common.b2ui(hdr, 36, 4); //offset 52 uint32_t ldata offset
		int tdata_offset = (int)Common.b2ui(hdr, 44, 4); //offset 60 uint32_t tdata offset
		//Check parameters
		if(!Common.in_range(spr_offset, 0, sff.length() ) || !Common.in_range(spr_count, 0, 65535) ||
			!Common.in_range(pal_offset, 0, sff.length() ) || !Common.in_range(pal_count, 0, 65535) ||
			!Common.in_range(ldata_offset, 0, sff.length() )|| 
			!Common.in_range(tdata_offset, 0, sff.length() ) ) {
			throw new SFFDecodeException("Bad header records!", 
				SFFDecodeException.BAD_FILE);
		}
		//Read all palette information (feature from sffv2)
		sff.seek(pal_offset); //seek to the top of palette data table
		palinfo = new SFFPaletteElement[pal_count];
		for(int i = 0; i < pal_count; i++) {
			//read 16 octet (single palette record)
			byte shdr[] = new byte[16];
			sff.read(shdr);
			int grp = (int)Common.b2ui(shdr, 0, 2); //Offset 0 uint16_t palette group number
			int pno = (int)Common.b2ui(shdr, 2, 2); //Offset 2 uint16_t palette item number
			int ncol = (int)Common.b2ui(shdr, 4, 2); //Offset 4 uint16_t Element count
			int lind = (int)Common.b2ui(shdr, 6, 2); //Offset 6 uint16_t link index
			int fileoff = (int)Common.b2ui(shdr, 8, 4); //Offset 8 uint32_t data offset
			int filelen = (int)Common.b2ui(shdr, 12, 4); //Offset 8 uint32_t data length
			SFFPaletteElement e = new SFFPaletteElement();
			e.groupid = grp;
			e.paletteid =  pno;
			e.numcols = ncol;
			e.linkid = lind;
			e.paloffset = fileoff + ldata_offset; //Actual palette offset: ldata_offset + this value
			e.pallength = filelen;
			//Check file offset and length
			if(!Common.in_range(e.paloffset, 0, sff.length() - filelen) ) {
				throw new SFFDecodeException(
					String.format("Plaette%d: Offset or length is out of file!", i),
					SFFDecodeException.BAD_SUBFILE, i);
			}
			//Check if size is ncol * 4 (palette is uint32_t array.)
			if(filelen != ncol * 4) {
				throw new SFFDecodeException(
					String.format("Plaette%d: Palette data size is weird!", i),
					SFFDecodeException.BAD_SUBFILE, i);
			}
			palinfo[i] = e;
		}
		//Read all sprite data
		sffinfo = new SFFElements[spr_count];
		sff.seek(spr_offset); //Seek to top of sprite data table
		for(int i = 0; i < spr_count; i++) {
			//Read next record, each record is 28 octets long
			byte[] shdr = new byte[28];
			sff.readFully(shdr);
			int grp = (int)Common.b2ui(shdr, 0, 2); //Offset 0 uint16_t Group number
			int ino = (int)Common.b2ui(shdr, 2, 2); //Offset 2 uint16_t Image number
			int iwidth = (int)Common.b2ui(shdr, 4, 2); //Offset 4 uint16_t Image width
			int iheight = (int)Common.b2ui(shdr, 6, 2); //Offset 6 uint16_t Image height
			int x = Common.b2i16(shdr, 8); //Offset 8 int16_t Center X
			int y = Common.b2i16(shdr, 10); //Offset 10 int16_t Center Y
			int lind = (int)Common.b2ui(shdr, 12, 2); //Offset 12 uint16_t link index
			int imgf = (int)Common.b2ui(shdr, 14, 1); //Offset 14 uint8_t image format type 0~4
			int cdep = (int)Common.b2ui(shdr, 15, 1); //Offset 15 uint8_t image color depth
			int file_off = (int)Common.b2ui(shdr, 16, 4); //Offset 16 uint32_t image offset
			int file_len = (int)Common.b2ui(shdr, 20, 4); //Offset 20 uint32_t image length
			int pal_index = (int)Common.b2ui(shdr, 24, 2); //Offset 24 uint16_t palette index
			int flags = (int)Common.b2ui(shdr, 26, 2); //Offset 26 uint16_t falgs
			//Check parameters
			if(!Common.in_range(imgf, 0, 4) || !Common.in_range(lind, 0, spr_count) ||
				!Common.in_range(pal_index, 0, pal_count) ) {
				throw new SFFDecodeException(
				String.format("Image %d: Bad image format, link id or palette id."
				, i) ,SFFDecodeException.BAD_FILE, i);
			}
			//if in link mode, link index should not point record itself
			if(lind == i && file_len == 0) {
				throw new SFFDecodeException(
					String.format("Image %d is linked image and pointing itself!", i),
					SFFDecodeException.BAD_FILE, i);
			}
			//Store data
			SFFElements e = new SFFElements();
			e.groupid = grp;
			e.imageid = ino;
			e.imgwidth = iwidth;
			e.imgheight = iheight;
			e.x = x;
			e.y = y;
			e.linkid = lind;
			e.imgtype = imgf;
			e.colordepth = cdep;
			e.paletteid = pal_index;
			e.flags = flags;
			//Data offset: file_off + ldata_offset (when flag=0), file_off + tdata_offset (when others)
			if(flags == 0) {
				e.imgoffset = file_off + ldata_offset;
			} else {
				e.imgoffset = file_off + tdata_offset;
			}
			e.imglength = file_len;
			//Check if image offset is in file
			if(!Common.in_range(e.imgoffset, 0, sff.length() - e.imglength) ) {
				throw new SFFDecodeException(
					String.format("Image %d: offset or length is out of file.", i),
					SFFDecodeException.BAD_FILE, i);
			}
			sffinfo[i] = e;
		}
	}
	
	//internal function to decode RLE8 image
	BufferedImage SFFv2DecodeRLE8Image(byte[] data, int imgw, int imgh, int[] pal, int ind)
		throws SFFDecodeException, IOException {
		BufferedImage img = new BufferedImage(imgw, imgh, BufferedImage.TYPE_INT_ARGB);
		int x = 0;
		int y = 0;
		int rl = -1;
		//first 4 octet represents uncompressed length of data
		//and it must be the same as imgw * imgh
		//and RLE8 is always for 256 indexed colour
		if(data.length < 4) {
			throw new EOFException(String.format("%d: RLE8 image data too short!", ind) );
		}
		int rawsize = (int)Common.b2ui(data, 0, 4);
		if(rawsize != imgw * imgh) {
			throw new SFFDecodeException(String.format("%d: RLE8 image uncompressed octet count is wrong!"),
				SFFDecodeException.BAD_SUBFILE, ind);
		}
		for(int c = 0;c < data.length - 4; c++) {
			int e = Byte.toUnsignedInt(data[c + 4]); //get 1 compressed octet
			//0b01xxxxxx is runlength, otherwise raw data
			if((e & 0xc0) != 0x40 || rl != -1) {
				//if not runlength or octet after runlength
				if(rl == -1) {
					rl = 1; //always assume runlength = 1
				}
				//Add pixel for runlength times
				for(int ii = 0; ii < rl; ii++) {
					int clr = pal[e];
					img.setRGB(x, y, clr);
					//advance x, we can use simple ++ method because java.awt.image.BufferedImage and sffv2 RLE8 has
					//same coordinate system
					x++;
					if(x >= imgw) {
						y++;
						x = 0;
						if(y >= imgh) { break; } //if y pointer reached to end, break loop
					}
				}
				rl = -1;
				if(y >= imgh) {break;} //if y pointer reached to end, break loop
			} else {
				rl = e - 0x40; //if e was 0b01xxxxxx, e - 0x40 is runlen.
			}
		}
		return img;
	}
	
	//Internal function to decode raw image to BufferedImage
	BufferedImage SFFv2DecodeRawImage(byte data[], int imgw, int imgh, int cdep, int pal[], int ind)
		throws SFFDecodeException, IOException {
		//Colordepth must be 8 (256 color index) or 24 (RGB888) or 32 (ARGB8888)
		//Currently, colordepth=24 or 32 is unsupported
		if(cdep != 8) {
			throw new SFFDecodeException(String.format("%d: Bad colordepth! (accepted values: 8)", ind),
				SFFDecodeException.BAD_SUBFILE, ind);
		}
		int colorocts = cdep / 8; //octet per pixel
		int x = 0;
		int y = 0;
		BufferedImage img = new BufferedImage(imgw, imgh, BufferedImage.TYPE_INT_ARGB);
		//Raw data format is simple array of {cdep} bit ints
		for(int i = 0; i < data.length / colorocts; i++) {
			//currently supports raw 8 bit colordepth (256 index color only)
			int e = Byte.toUnsignedInt(data[i]);
			img.setRGB(x, y, pal[e]);
		}
		return img;
	}
	
	/**
		Returns reading sff file format version.
		@return 1: sffv1 2: sffv2
	*/
	public int GetSFFVersion() {
		return SFFVersion;
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
		//If image length appars to 0, image is linked one
		//in sffv2, image type will be 1 (INVALID) too.
		if(e.imglength == 0) {
			return e.linkid;
		}
		return -1;
	}
	
	/**
		SFFv1 only. Returns image mode of image at specified index.
		@param imgid Image index in order of sff subfiles.
		@return Shared palette or not. False if sffv2
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public boolean IsSharedPalette(int imgid) {
		if(GetSFFVersion() == SFF_V2) { return false; }
		return sffinfo[imgid].shared;
	}
	
	/**
		SFFv2 only. Returns image data type of image at specified index.
		0: RAW, 1: INVALID(Linked) , 2: RLE8, 3: RLE5, 4: LZ5
		-1: no param (SFFv1, always 256 indexed colour pcx)
		@param imgid Image index in order of sff subfiles.
		@return Image type Id, -1 if reading sffv1 (always pcx data type)
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int SFFv2GetImageType(int imgid) {
		if(GetSFFVersion() == SFF_V1) {
			return -1;
		}
		return sffinfo[imgid].imgtype;
	}
	
	/**
		SFFv2 only. Returns colour depth of image at specified index.
		@param imgid Image index in order of sff subfiles.
		@return Image colour depth, -1 if reading SFFv1 (Parameter added in sffv2).
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int SFFv2GetImageColorDepth(int imgid) {
		if(GetSFFVersion() == SFF_V1) {
			return -1;
		}
		return sffinfo[imgid].colordepth;
	}
	
	/**
		SFFv2 only. Returns palette index of image at specified index.
		@param imgid Image index in order of sff subfiles.
		@return Palette index, -1 if reading SFFv1 (Parameter added in sffv2).
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int SFFv2GetImagePaletteIndex(int imgid) {
		if(GetSFFVersion() == SFF_V1) {
			return -1;
		}
		return sffinfo[imgid].paletteid;
	}
	
	/**
		SFFv2 only Returns size of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return dimension of image, null if reading SFFv1 (Parameter added in sffv2).
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public Dimension SFFv2GetImageSize(int imgid) {
		SFFElements e = sffinfo[imgid];
		if(GetSFFVersion() == SFF_V1) {
			return null;
		}
		return new Dimension(e.imgwidth, e.imgheight);
	}
	
	/**
		SFFv2 only. Returns image width of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return width of image, -1 if reading SFFv1 (Parameter added in sffv2).
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int SFFv2GetImageWidth(int imgid) {
		if(GetSFFVersion() == SFF_V1) {
			return -1;
		}
		return sffinfo[imgid].imgwidth;
	}
	
	/**
		SFFv2 only Returns image height of image at specified index
		@param imgid Image index in order of sff subfiles.
		@return height of image, -1 if reading SFFv1 (Parameter added in sffv2).
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int SFFv2GetImageHeight(int imgid) {
		if(GetSFFVersion() == SFF_V1) {
			return -1;
		}
		return sffinfo[imgid].imgheight;
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
			r = new byte[e.imglength];
			sff.seek(e.imgoffset);
			sff.readFully(r);
		}
		return r;
	}
	
	/**
		SFFv1 only. Returns palette data of image at specified index
		@param imgid Image index in order of sff subfiles. null if SFFv2.
		@return 256 color palette int[], ARGB8888.
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
		@throws IOException when IO Error occured.
	*/
	public int[] GetPalette(int imgid) throws IOException {
		//SFFv2 will use different method to store palette..
		if(GetSFFVersion() == SFF_V2) {
			return null;
		}
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
			pal[i] = (int)Common.b2uibe(data, paloff + (i * 3) + 1, 3); //get RGB888 value
			//convert to ARGB8888
			//index 0 colour is always transparent, alpha=255 except index 0
			if(i != 0) {pal[i] += 0xff000000;}
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
		@throws SFFDecodeException when image format was bad.
	*/
	public BufferedImage ConvertImage(int imgid)  throws IOException, SFFDecodeException {
		if(GetSFFVersion() == SFF_V1) {
			//SFFv1 allows only pcx, decode.
			return DecodePCXImage(imgid);
		} else if(GetSFFVersion() == SFF_V2) {
			//SFFv2, uncompressed, rle5, lz5, rle8, png8, png24 and png32 are possible
			//if image is linked, forward link
			int linkstate = GetLinkState(imgid);
			if(linkstate != -1) {
				return ConvertImage(linkstate);
			}
			//Get palette
			int palind = SFFv2GetImagePaletteIndex(imgid);
			int pal[] = SFFv2GetPalette(palind);
			//Get image information
			int imgw = SFFv2GetImageWidth(imgid);
			int imgh = SFFv2GetImageHeight(imgid);
			int imgc = SFFv2GetImageColorDepth(imgid);
			int imgt = SFFv2GetImageType(imgid);
			byte data[] = GetRawImage(imgid); //Get raw image data
			//Currently rle8 and uncompressed are supported format
			if(imgt == SFFV2_IMGTYPE_RAW) {
				return SFFv2DecodeRawImage(data, imgw, imgh, imgc, pal, imgid);
			} else if(imgt == SFFV2_IMGTYPE_RLE8) {
				return SFFv2DecodeRLE8Image(data, imgw, imgh, pal, imgid);
			} else {
				throw new SFFDecodeException(String.format("%d: Bad format or unsupported: type%d depth%d", imgid, imgt, imgc),
					SFFDecodeException.BAD_SUBFILE, imgid);
			}
		}
		return null; //Unreachable
	}
	
	BufferedImage DecodePCXImage(int imgid) throws IOException, SFFDecodeException {
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
		SFFv2 only. Returns link id of palette at specified index.
		Returns -1 if the palette is not linked one, or reading sffv1.
		@param palid Palette index in order of sff subfiles.
		@return if image is linked, destination image index. or -1 if not linked or sffv1.
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int SFFv2GetPaletteLinkState(int palid) {
		SFFPaletteElement e = palinfo[palid];
		if(GetSFFVersion() == SFF_V2) {
			return -1;
		}
		//If palette length appars to 0, palette is linked one
		if(e.pallength == 0) {
			return e.linkid;
		}
		return -1;
	}
	
	/**
		SFFv2 only. Returns color count of palette at specified index.
		Returns -1 if reading sffv1.
		@param palid Palette index in order of sff subfiles.
		@return Palette colour count, or -1 if reading sffv1
		@throws ArrayIndexOutOfBoundsException when imgid is greater than image count.
	*/
	public int SFFv2GetPaletteSize(int palid) {
		if(GetSFFVersion() == SFF_V1) {
			return -1;
		}
		return palinfo[palid].numcols;
	}
	
	/**
		SFFv2 only. Returns total palette count.
		Returns 0 if reading sffv1.
		@return Palette count, or 0 if reading sffv1
	*/
	public int SFFv2GetPaletteCount() {
		if(GetSFFVersion() == SFF_V1) {
			return 0;
		}
		return palinfo.length;
	}
	
	/**
		SFFv2 only. Get palette data of specified index then convert it ARGB8888 int[]
		@param palid Palette index in order of sff subfiles.
		@return Palette data, int[], in order of colour index, each element is ARGB8888
		@throws ArrayIndexOutOfBoundsException when palid is greater than palette count.
		@throws IOException when IO Error occured.
		@throws EOFException when image data is shorter than excepted.
	*/
	public int[] SFFv2GetPalette(int palid) throws IOException {
		//SFFv2 only, return if v1
		if(GetSFFVersion() == SFF_V1) {
			return null;
		}
		int linkstate = SFFv2GetPaletteLinkState(palid);
		if(linkstate != -1) {
			return SFFv2GetPalette(linkstate);
		}
		SFFPaletteElement e = palinfo[palid];
		//seek to palette data offset
		sff.seek(e.paloffset);
		//read palette
		byte data[] = new byte[e.pallength];
		sff.readFully(data);
		//decode palette, it is RGBA8888 array
		int[] _r = new int[SFFv2GetPaletteSize(palid)];
		for(int i = 0; i < _r.length; i++) {
			//Converting RGBA8888 to ARGB8888
			_r[i] = (int)Common.b2uibe(data, i * 4, 3); //GET RGB888
			//alpha=255 except index 0
			if(i != 0) { _r[i] = _r[i] + 0xff000000; }
		}
		return _r;
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
	
	/**
		for test, currently outputs 0, 0 image of specified sff as output.png or
		output.raw and output.act combo.
	*/
	public static void main(String args[]) throws Exception {
		//Open SFF
		KumoSFFReader sr = new KumoSFFReader(args[0]);
		//Get 0, 0 Image (Small Portrait)
		int i = sr.FindIndexByNumbers(9000, 1);
		if(i == -1) {
			i = sr.FindIndexByNumbers(9000, 0);
		}
		if(i != -1) {
			BufferedImage b = sr.ConvertImage(i);
			ImageIO.write(b, "png", new File("output.png"));
		}
		System.out.printf("Total image count: %d\n", sr.GetImageCount() );
		if(sr.GetSFFVersion() == SFF_V2) {
			System.out.println("SFF Version 2");
			System.out.printf("Total palette count: %d\n", sr.SFFv2GetPaletteCount());
		}
		sr.closeSFF(); //close
	}
	
	/**
		Return library version string.
		@return Version string
	*/
	public static String GetLibVersion() {
		return VER;
	}
	
	/**
		Returns library information string
		@return Library information string
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
