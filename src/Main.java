
import serveurstf.ServeurSTF;


/**
 * Class Main
 * 
 * @author Mélanie DUBREUIL
 * @author Ophélie EOUZAN
 */
public class Main
{
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        ServeurSTF tftp = new ServeurSTF("localhost");
        tftp.receiveFile("C:\\Users\\Epulapp\\Documents\\Cours\\Réseau\\A4\\TP_TFTP\\PumpkinDirectory\\animal.png", "image02.png");
    }
}
