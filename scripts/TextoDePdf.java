import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/**
 * El texto de un PDF, página a página, para que el comparador de contrato
 * compare lo que DICE un documento de Django y el de Kotlin: los bytes no
 * pueden coincidir (reportlab y OpenPDF son motores distintos).
 *
 *     java -cp openpdf.jar scripts/TextoDePdf.java documento.pdf
 */
public class TextoDePdf {
    public static void main(String[] argumentos) throws Exception {
        PdfReader lector = new PdfReader(argumentos[0]);
        PdfTextExtractor extractor = new PdfTextExtractor(lector);
        System.out.println("PÁGINAS " + lector.getNumberOfPages());
        for (int i = 1; i <= lector.getNumberOfPages(); i++) {
            System.out.println(extractor.getTextFromPage(i));
        }
    }
}
