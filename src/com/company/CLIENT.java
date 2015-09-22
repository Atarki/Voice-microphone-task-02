package com.company;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class CLIENT {
    static final Object synchonizer = new Object();
    static volatile boolean running;
    static Socket socket;
    static volatile byte[] data;
    static volatile int numBytesRead;
    static volatile int senderNotReady = 0;
    static volatile int sendersCreated = 0;
    static MicrophoneReader micReader;
    static Sender sender;

    public static void main(String[] args) throws IOException {
        try {
            micReader = new MicrophoneReader();
            micReader.start();

//            Socket s2 = new Socket("192.168.0.2", 8081);
            Socket s2 = new Socket("92.249.120.254", 8081);
//            Socket s2 = new Socket("localhost", 8081);

//            ServerSocket serverSocket = new ServerSocket(8081);
            System.out.println(s2.isConnected());

            ReceiverPlayer receiverPlayer = new ReceiverPlayer(s2);
            receiverPlayer.start();


            while (true) {
//                Socket clientSocket = serverSocket.accept();

//                sender = new Sender(clientSocket);
                sender = new Sender(s2);
                sender.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            micReader.setRunning();
            sender.setRunning();
        }
    }


    static class Sender extends Thread {

        volatile boolean running = false;

        public Sender(Socket s) {
            com.company.CLIENT.socket = s;
        }

        public void setRunning() {
            running = true;
        }

        @Override
        public void run() {
            try {
                OutputStream os = socket.getOutputStream();

                while (running) {
                    synchronized (synchonizer) {
                        senderNotReady++;

                        synchonizer.wait();

                        os.write(data, 0, numBytesRead);
                        os.flush();

                        senderNotReady--;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class MicrophoneReader extends Thread {

        volatile boolean running = false;

        public void setRunning() {
            running = false;
        }

        @Override
        public void run() {
            try {
                AudioFormat audioFormat = new AudioFormat(16000.0f, 16, 2, true, true);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
                TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(audioFormat);
                microphone.start();

                int bufferSize = (int) audioFormat.getSampleRate() * audioFormat.getFrameSize();
                data = new byte[bufferSize];

                numBytesRead = microphone.read(data, 0, bufferSize);
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                running = true;
                while (running) {
                    synchronized (synchonizer) {
                        if (senderNotReady == sendersCreated) {
                            synchonizer.notifyAll();
                            continue;
                        }
                        if (numBytesRead > 0) {
                            out.write(data, 0, numBytesRead);
                        }
                    }
                }
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        }
    }
    public static class ReceiverPlayer extends Thread {

        public ReceiverPlayer(Socket s) {
            com.company.CLIENT.socket = s;
        }


        public void run() {
            try {
                String host = "localhost";
                InetAddress ipAddr = InetAddress.getByName(host);

                Socket s = new Socket(ipAddr, 8081);
                InputStream is = socket.getInputStream();

                AudioFormat format = new AudioFormat(16000.0f, 16, 2, true, true);
                DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(dataLineInfo);

                speakers.open(format);
                speakers.start();

                int numBytesRead;

                byte[] data = new byte[204800];

                while (true) {
                    numBytesRead = is.read(data);
                    speakers.write(data, 0, numBytesRead);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
