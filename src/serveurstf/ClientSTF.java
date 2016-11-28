package serveurstf;

import UnsignedHelper.UnsignedHelper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Ophélie EOUZAN
 * @author Mélanie DUBREUIL
 * 
 */
public class ClientSTF {
    // TFTP OP CODE    
    public static final byte OP_ZERO = 0;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATA = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;
    
    // ERROR CODES
    private static final int ERR_SOCKET = -1;
    private static final int ERR_PACKET = -2;
    private static final int ERR_TIMEOUT = -3;
    private static final int ERR_FILEUNKNOWN = -4;
    private static final int ERR_HOST = -5;
    
    public final int TEMPO = 10;
    public final int BUFFER = 512; // 512 + 4

    private int code = 0;
    private String errorMsg = "";
    private DatagramSocket ds;
    private InetAddress inetServerAddress;
    private int serverPort;
    public String mode = "octet";
    private Exception exception = null;
    
    public ClientSTF(String ip, int port)
    {
        try {
            serverPort = port;
            inetServerAddress = InetAddress.getByName(ip);

            ds = new DatagramSocket();
            // ds.setSoTimeout(TEMPO);
        } catch (IOException ex) {
            if (ex instanceof SocketException) {
                code = ERR_SOCKET;
            } else if (ex instanceof UnknownHostException) {
                code = ERR_HOST;
            } else {
                code = ERR_PACKET;
            }
            errorMsg = "Return code : " + code + " [ " + ex.getClass() + " - " + ex.getMessage() + " ] ";
            //Logger.getLogger(ClientSTF.class.getName()).log(Level.SEVERE, errorMsg);
            System.out.println(errorMsg);
        }
    }
    
    public int receiveFile(String localFileName, String remoteFileName)
    {
        try {
            // Create local file
            File localFile = new File(localFileName);
            FileOutputStream fos;
            fos = new FileOutputStream(localFile);

            // Send Read request
            this.sendWriteOrSendRequest(remoteFileName, OP_RRQ);
            
            // Recieve file
            int ackNumber = 0;
            byte[] data = new byte[BUFFER + 4];
            DatagramPacket recieveDatagram;

            do {
                System.out.println("Waiting packet...");
                // Recieve data
                recieveDatagram = new DatagramPacket(data, data.length);
                ds.receive(recieveDatagram);
                data = recieveDatagram.getData();

                // Get block number OR code error
                String numberString = new Byte(data[2]).intValue() + "" + new Byte(data[3]).intValue(); //((dataReceived[2] & 0xff) << 8) | (dataReceived[3] & 0xff);
                byte[] numberBytes = {data[2], data[3]};
                int number = (data[2] & 0xff << 8)|(data[3] & 0xff);//UnsignedHelper.twoBytesToInt(numberBytes);//Integer.valueOf(numberString);
                
                System.out.print("Received packet " + number + " : ");

                // Read op code
                if(data[1] == OP_DATA) {

                    System.out.println("DATA size " + recieveDatagram.getLength());
                    if (number != ackNumber) {
                        // Write data in file
                        fos.write(data, 4, data.length - 4);
                        System.out.println("Write DATA " + number);
                    }

                    // Create ACK request
                    numberBytes = UnsignedHelper.intTo2UnsignedBytes(number);
                    byte[] ack = { 0, OP_ACK, numberBytes[0], numberBytes[1] }; //(byte)(data[2]*256 & 255), (byte)(data[3] & 255)
                    ackNumber = Integer.valueOf(new Byte(data[2]).intValue() + "" + new Byte(data[3]).intValue());

                    // Send ack datagram
                    DatagramPacket ackDatagram = new DatagramPacket(ack, 4, inetServerAddress, recieveDatagram.getPort());
                    ds.send(ackDatagram);
                    System.out.println("Send ACK " + number);
                } else if(data[1] == OP_ERROR) {
                    System.out.println("ERROR");
                    code = number + 1;
                    errorMsg = new String(data, 4, data.length - 5);
                    break;
                }
            } while (recieveDatagram.getLength() == (BUFFER + 4));
            
            // Close local file
            fos.close();
        } catch (IOException ex) {
            if(ex instanceof InterruptedIOException) {
                code = ERR_TIMEOUT;
            } else if(ex instanceof SocketException) {
                code = ERR_SOCKET;
            } else if (ex instanceof FileNotFoundException) {
                code = ERR_FILEUNKNOWN;
            } else {
                code = ERR_PACKET;
            }
            exception = ex;
        } finally {
            ds.close();
            
            if (exception != null) {
                errorMsg = exception.getClass() + " - " + exception.getMessage();
                //exception.printStackTrace();
                Logger.getLogger(ClientSTF.class.getName()).log(Level.SEVERE, errorMsg);
            }
            System.out.println("Return code : " + code + " [ " + errorMsg + " ] ");

            return code;
        }
    }

    public int sendFile(String localFileName) {
        code = 0;
        FileInputStream fileReader;        

        try {
            DatagramPacket receivedAckPacket;
            byte[] buffer = new byte[BUFFER];
            
            // Opening the file
            fileReader = new FileInputStream(localFileName);
            this.sendWriteOrSendRequest(localFileName, OP_WRQ);
            
            int blockNumber = 1;
            int dataBlockSize = 0;
            do { 
                byte[] data = new byte[BUFFER];
                System.out.println("Wait ACK");
                receivedAckPacket = new DatagramPacket(data, BUFFER);
                ds.receive(receivedAckPacket);

                int receivedPacketNumber = (int)(data[2]*256 & 255) + (int)(data[3] & 255);
                byte[] opCode = { data[0], data[1] };
                if (opCode[1] == OP_ACK) {
                    System.out.println("ACK  : " + receivedPacketNumber);
                    if (receivedPacketNumber == blockNumber - 1) {
                        dataBlockSize = fileReader.read(buffer, 0, BUFFER);
                    }
                    System.out.println("Size of Data to send :" + dataBlockSize);
                    if (dataBlockSize != -1) {
                        System.out.println("Send data : " + blockNumber);
                        byte[] dataToSend = this.createDataPacket(buffer, dataBlockSize, blockNumber);
                        ds.send(new DatagramPacket(dataToSend, dataToSend.length, inetServerAddress, receivedAckPacket.getPort()));
                        blockNumber++;
                    }
                } else if (opCode[1] == OP_ERROR){
                    System.out.println("ERROR");
                    code = receivedPacketNumber + 1;
                    errorMsg = new String(data, 4, data.length - 5);
                    break;
                }
            } while(dataBlockSize != -1); // Tant qu'il y a des données à envoyer
            fileReader.close();
        } catch (IOException ex) {
            //Logger.getLogger(ClientSTF.class.getName()).log(Level.SEVERE, null, ex);
            if(ex instanceof FileNotFoundException) {
                code = ERR_FILEUNKNOWN; // TODO
            }
            exception = ex;
        } finally {
            ds.close();

            if (exception != null) {
                errorMsg = " [ " + exception.getClass() + " - " + exception.getMessage() + " ] ";
                //exception.printStackTrace();
            }
            System.out.println("Return code : " + code + errorMsg);
                    
            return code;
        }
    }
    
    public void sendWriteOrSendRequest(String remoteFileName, byte opCode) throws IOException
    {
        // Packet : 2 OP_CODE bytes - n FILE NAME bytes - 1 ZERO byte - n FILE LENGTH bytes for - 1 ZERO byte
        int requestLength = 2 + remoteFileName.length() + 1 + mode.length() + 1;
        byte[] request = new byte[requestLength];
        
        // Request content
        int i = 0;
        request[i] = OP_ZERO;
        request[++i] = opCode;
        for (int y = 0; y < remoteFileName.length(); y++) {
            request[++i] = (byte) remoteFileName.charAt(y);
        }
        request[++i] = OP_ZERO;
        for (int y = 0; y < mode.length(); y++) {
            request[++i] = (byte) mode.charAt(y);
        }
        request[++i] = OP_ZERO;

        // Sending request to the server
        ds.send(new DatagramPacket(request, request.length, inetServerAddress, serverPort));
    }
    
    private byte[] createDataPacket(byte[] byteRead, int length, int blockNumber){
        byte[] data = new byte[4 + length];
        System.out.println(data.length);
        
        // Packet creation
        data[0] = OP_ZERO;
        data[1] = OP_DATA;
        data[2] = (byte)(blockNumber/256);
        data[3] = (byte)(blockNumber%256);
        
        for(int position = 0 ; position < length; position++){
            if (position >= data.length){
                break;
            }
            data[position + 4] = byteRead[position];
        }
        //Arrays.copyOfRange(data, 4, data.length);

        return data;
    }
}
