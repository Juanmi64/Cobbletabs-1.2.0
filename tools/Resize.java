import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Reescala logo.png para que quepa en un lienzo de 96x96 sin deformarse
 * (mantiene la proporción y centra el resultado sobre fondo transparente).
 */
public class Resize {
	public static void main(String[] args) throws Exception {
		int size = args.length >= 3 ? Integer.parseInt(args[2]) : 96;
		// Porcentaje del lienzo que ocupa el arte (100 = sin margen; 60 = 40% de margen transparente)
		int fill = args.length >= 4 ? Integer.parseInt(args[3]) : 100;
		BufferedImage src = ImageIO.read(new File(args[0]));
		if (src == null) {
			System.err.println("No se pudo leer la imagen: " + args[0]);
			System.exit(1);
		}
		int w = src.getWidth();
		int h = src.getHeight();
		double scale = Math.min((double) size * fill / 100.0 / w, (double) size * fill / 100.0 / h);
		if (scale > 1.0) {
			scale = 1.0; // no ampliar por encima del original para no perder nitidez
		}
		int nw = Math.max(1, (int) Math.round(w * scale));
		int nh = Math.max(1, (int) Math.round(h * scale));

		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(src, (size - nw) / 2, (size - nh) / 2, nw, nh, null);
		g.dispose();

		ImageIO.write(out, "png", new File(args[1]));
		System.out.println("OK: " + w + "x" + h + " -> " + nw + "x" + nh + " centrado en lienzo " + size + "x" + size);
	}
}
