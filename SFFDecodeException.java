package kumotechmadlab.sffreader;

/**
	Exception thrown when there is a bad record in file, sff version is incompatible or
	not a sff file.
*/
public class SFFDecodeException extends Exception {
	/** File magic byte is wrong */
	public static final int WRONG_IDENT = 0;
	/** Incompatible ver */
	public static final int BAD_VER = 1;
	/** Bad record in header */
	public static final int BAD_FILE = 2;
	/** Bad record in subheader */
	public static final int BAD_SUBFILE = 3;
	/** missing needed data */
	public static final int MISSING_DATA = 4;
	
	final int ErrorNumber;
	final int ErroredSubfileNumber;
	
	/**
		Initialize SFFDecodeException with message and reason Id.
		See getErrorNumber() for errnum
		@param errorMessage Message
		@param errnum Reason Id
	*/
	public SFFDecodeException(String errorMessage, int errnum) {
		super(errorMessage);
		ErrorNumber = errnum;
		ErroredSubfileNumber = -1;
	}
	
	/**
		Initialize SFFDecodeException with message, reason Id and errored subfile index
		See getErrorNumber() for errnum
		@param errorMessage Message
		@param errnum Reason Id
		@param sf Errored subfile index
	*/
	public SFFDecodeException(String errorMessage, int errnum, int sf) {
		super(errorMessage);
		ErrorNumber = errnum;
		ErroredSubfileNumber = sf;
	}
	
	/**
		Return error reason ID
		0: Not a sff 1: Incompatible Version 2: Bad record 3: Bad Subfile 4:Missing needed data
		@return Error Id
	*/
	public int getErrorNumber() {
		return ErrorNumber;
	}
	
	/**
		Get errored subfile number. Returns -1 if not specified.
		@return Errored Subfile Index
	*/
	public int getErroredSubfileIndex() {
		return ErroredSubfileNumber;
	}
}
