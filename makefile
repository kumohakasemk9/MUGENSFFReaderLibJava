OBJS=C.class DConv.class SFFElements.class SFFPaletteElement.class SFFDecodeException.class KumoSFFReader.class
OUTDIR=kumotechmadlab
OUT=KSReader.jar

all: $(OBJS)
	jar cvf $(OUT) $(OUTDIR)

docs:
	javadoc -d docs KumoSFFReader.java SFFDecodeException.java

clean:
	rm -rf $(OUTDIR) $(OUT)

%.class: %.java
	javac -d . $<
