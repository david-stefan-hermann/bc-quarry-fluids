import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;
/** java tools/Crop.java in.png out.png x y w h */
public class Crop { public static void main(String[] a) throws Exception { BufferedImage img = ImageIO.read(new File(a[0])); int x=Integer.parseInt(a[2]), y=Integer.parseInt(a[3]), w=Integer.parseInt(a[4]), h=Integer.parseInt(a[5]); ImageIO.write(img.getSubimage(x, y, w, h), "png", new File(a[1])); System.out.println("cropped " + w + "x" + h); } }
