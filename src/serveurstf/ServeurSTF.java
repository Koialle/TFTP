
package serveurstf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Ophélie EOUZAN
 */
public class ServeurSTF { // Classe de Tests des primitives receive and send

    public final int BUFFER = 512;
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
    }
    
    public int receivefile(String localFileName, String remoteFileName, String remoteAddr)
    {
        int offset = 0;
        File localFile = new File(localFileName);
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(localFile);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ServeurSTF.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // Create RRQ request
        String mode = "octet";
        byte zeroByte = 0;
        int readRequestLength = 2 + remoteFileName.length() + 1 + mode.length() + 1;
	byte[] readRequest = new byte[readRequestLength];
        
        try {
            DatagramSocket ds = new DatagramSocket();
        } catch (SocketException ex) {
            Logger.getLogger(ServeurSTF.class.getName()).log(Level.SEVERE, null, ex);
        }
        DatagramPacket readRequestDatagram = new DatagramPacket(readRequest, readRequestLength);
        File remoteFile = new File(remoteFileName);
        
        // Write into local file
        byte[] data = new byte[BUFFER];
        data = datagramPacket.getData();
        
        fos.write(data, offset, datagramPacket.getLenght());
        fos.close();
        
    }
}
