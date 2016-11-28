
import java.util.Scanner;
import serveurstf.ClientSTF;


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
    public static void main(String[] args)
    {
        
        Scanner reader = new Scanner(System.in, "UTF-8");
        String filePath = "", serverAddress = "", cmd = "", fileName;
        int serverPort=-1;
        
        System.out.print("\nServer address: ");
        serverAddress = reader.next();
        System.out.print("\nServer port: ");
        serverPort = reader.nextInt();
        
        ClientSTF tftp = new ClientSTF(serverAddress, serverPort); // Test EPULLAPP80
        
//        while (!cmd.equalsIgnoreCase("stop")) {
            while(!cmd.equalsIgnoreCase("r") && !cmd.equalsIgnoreCase("s")) {
                System.out.print("\nSend or receive (R/S): ");
                cmd = reader.next();
            }
            System.out.print("\nLocal file path: ");
            filePath = reader.next();
            
            if (cmd.equalsIgnoreCase("s")) {
                tftp.sendFile(filePath); //"C:\\Users\\Epulapp\\Documents\\Cours\\Réseau\\A4\\TP_TFTP\\Files\\image02.png"
            } else if (cmd.equalsIgnoreCase("r")) {
                System.out.print("\nFile name (with extension): ");
                fileName = reader.next();
                tftp.receiveFile(filePath, fileName); //"C:\\Users\\Epulapp\\Documents\\Cours\\Réseau\\A4\\TP_TFTP\\Files\\animal.jpg", "tigre.jpg"
            }
//        }       
    }
}
