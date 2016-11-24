
package serveurstf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Ophélie EOUZAN
 */
public class ServeurSTF { // Classe de Tests des primitives receive and send

    public final int BUFFER = 516; // 512 + 4
    public final int TEMPO = 10;
    
    // TFTP OP CODE
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATA = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;
    
    private DatagramSocket ds;
    private int code = 0;
    private InetAddress inetServerAddress;
    private int serverPort;
    
    private Exception exception = null;
    
    public ServeurSTF(String ip) 
    {
        try {
            serverPort = 69;
            inetServerAddress = InetAddress.getByName(ip);

            ds = new DatagramSocket();
//            ds.setSoTimeout(TEMPO);
        } catch (SocketException ex) {
            Logger.getLogger(ServeurSTF.class.getName()).log(Level.SEVERE, null, ex);
            code = -1;
        } catch (UnknownHostException ex) {
            Logger.getLogger(ServeurSTF.class.getName()).log(Level.SEVERE, null, ex);
            code = -6;
        }
    }
    
    public int receiveFile(String localFileName, String remoteFileName)
    {
        try {
            // Create local file
            File localFile = new File(localFileName);
            FileOutputStream fos;
            fos = new FileOutputStream(localFile);
            
            // Create RRQ request tab
            String mode = "octet";
            int readRequestLength = 2 + remoteFileName.length() + 1 + mode.length() + 1;
            byte[] readRequest = new byte[readRequestLength];
            
            // Create RRQ request content
            int i = 0;
            byte zero = 0;
            readRequest[i] = zero;
            readRequest[++i] = OP_RRQ;
            for (int y = 0; y < remoteFileName.length(); y++) {
                readRequest[++i] = (byte) remoteFileName.charAt(y);
            }
            readRequest[++i] = zero;
            for (int y = 0; y < mode.length(); y++) {
                readRequest[++i] = (byte) mode.charAt(y);
            }
            readRequest[++i] = zero;

            // Send Read request
            DatagramPacket readRequestDatagram = new DatagramPacket(readRequest, readRequestLength, inetServerAddress, serverPort);
            ds.send(readRequestDatagram);
            
            // Recieve file
            int offset = 0;
            int ackNumber = 0;
            byte[] data = new byte[BUFFER];
            DatagramPacket recieveDatagram;

            do {
                // Recieve data
                recieveDatagram = new DatagramPacket(data, BUFFER);
                ds.receive(recieveDatagram);
                data = recieveDatagram.getData();

                // Get block number OR code error
                String numberString = new Byte(data[2]).intValue() + "" + new Byte(data[3]).intValue(); //((dataReceived[2] & 0xff) << 8) | (dataReceived[3] & 0xff);
                int number = Integer.valueOf(numberString);
                
                System.out.print("Received packet "+number+" :");

                // Read op code
                if(data[1] == OP_DATA) {

                    System.out.println("DATA");
                    if (number != ackNumber) { // Comparer au numéro d'ACK envoyé, et faire l'écriture seulement si ACK != data block number, else juste envoyer le ACK
                        // Write data in file
                        //byte[] d = Arrays.copyOfRange(data, 4, data.length);
                        //fos.write(d, offset, d.length);
                        fos.write(data, 4, data.length - 4);
//                        offset += d.length;
                        System.out.println("Write DATA "+number);
                    }

                    // Create ACK request
                    byte[] ack = { 0, OP_ACK, data[2], data[3] };
                    ackNumber = Integer.valueOf(new Byte(data[2]).intValue() + "" + new Byte(data[3]).intValue());

                    // Send ack datagram
                    DatagramPacket ackDatagram = new DatagramPacket(ack, 4, inetServerAddress, recieveDatagram.getPort());
                    ds.send(ackDatagram);
                    System.out.println("Send ACK "+number);
                } else if(data[1] == OP_ERROR) {
                    System.out.println("ERROR");
                    code = number;
                }
            } while (recieveDatagram.getLength() == BUFFER);
            
            // Close local file
            fos.close();
        } catch (IOException ex) {
            code = -5;
            if(ex instanceof InterruptedIOException) {
                code = -2;
            }
            exception = ex;
        } finally {
            ds.close();
            
            if (exception != null) {
                Logger.getLogger(ServeurSTF.class.getName()).log(Level.SEVERE, null, exception);
                exception.printStackTrace();
            }
            
            return code;
        }
    }
}
//        2 bytes  2 bytes        string    1 byte
//        ----------------------------------------
// ERROR | 05    |  ErrorCode |   ErrMsg   |   0  |
//        ----------------------------------------
//
//Error Codes
//
//   Value     Meaning
//
//   0         Not defined, see error message (if any).
//   1         File not found.
//   2         Access violation.
//   3         Disk full or allocation exceeded.
//   4         Illegal TFTP operation.
//   5         Unknown transfer ID.
//   6         File already exists.
//   7         No such user.