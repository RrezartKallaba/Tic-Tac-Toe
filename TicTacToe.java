import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class TicTacToe implements Runnable {

    private final String ip = "localhost";
    private final int port = 22222;
    private final Scanner scanner = new Scanner(System.in);
    private JFrame frame;
    private final int WIDTH = 606;
    private final int HEIGHT = 750;
    private Thread thread;

    private int boardX; // Pozicioni X i foton e tavolines
    private int boardY; // Pozicioni Y i foton e tavolines
    private Painter painter;
    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;

    private ServerSocket serverSocket;

    private BufferedImage board;
    private BufferedImage redX;
    private BufferedImage blueX;
    private BufferedImage redCircle;
    private BufferedImage blueCircle;

    private String[] spaces = new String[9];

    private boolean yourTurn = false;
    private boolean opponentTurn = false;

    private boolean circle = true;
    private boolean accepted = false;
    private boolean unableToCommunicateWithOpponent = false;
    private boolean won = false;
    private boolean enemyWon = false;
    private boolean tie = false;

    private String username;

    private int lengthOfSpace = 160;
    private int errors = 0;
    private int firstSpot = -1;
    private int secondSpot = -1;

    private int player1Timer = 10; // Koha fillestare per lojtarin 1 (ne sekonda)
    private int player2Timer = 10; // Koha fillestare per lojtarin 2 (ne sekonda)
    private boolean timerExpired = false; // Tregon nese timeri ka skaduar
    private int player1TimerDisplay = 10; // Koha e shfaqur per lojtarin 1 (ne sekonda)
    private int player2TimerDisplay = 10; // Koha e shfaqur per lojtarin 2 (ne sekonda)

    private boolean timerRunning = false; // Tregon nese timer eshte duke u ekzekutuar

    private Font font = new Font("Verdana", Font.BOLD, 32);
    private Font smallerFont = new Font("Verdana", Font.BOLD, 20);
    private Font largerFont = new Font("Verdana", Font.BOLD, 50);

    private String waitingString = "Waiting for another player";
    private String unableToCommunicateWithOpponentString = "Unable to communicate with opponent.";
    private String wonString = "You won!";
    private String enemyWonString = "Opponent won!";
    private String tieString = "Game ended in a tie.";

    private int[][] wins = new int[][] { { 0, 1, 2 }, { 3, 4, 5 }, { 6, 7, 8 }, { 0, 3, 6 }, { 1, 4, 7 }, { 2, 5, 8 },
            { 0, 4, 8 }, { 2, 4, 6 } };

    public TicTacToe() {
        System.out.println("Please input the name: ");
        String name = scanner.nextLine();
        username = name;
        System.out.println("Please input the IP: ");
        String ip = scanner.nextLine();
        System.out.println("Please input the port: ");
        int port = scanner.nextInt();
        System.out.println("User ip: " + ip);
        // System.out.println("Username for player 1: " + username);
        // System.out.println("Username for player 2: " + username);
        while (port < 1 || port > 65535) {
            System.out.println("The port you entered was invalid, please input another port: ");
            port = scanner.nextInt();
        }

        loadImages();

        painter = new Painter();
        painter.setPreferredSize(new Dimension(WIDTH, HEIGHT));

        if (!connect())
            initializeServer();

        frame = new JFrame();
        frame.setTitle("Tic-Tac-Toe - Player: " + username);
        frame.setContentPane(painter);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);

        // Llogarit pozicionin e foton e tavolines per ta vendosur ne qender
        boardX = (WIDTH - board.getWidth()) / 2;
        boardY = (HEIGHT - board.getHeight()) / 2;

        thread = new Thread(this, "TicTacToe");
        thread.start();
    }

    public void run() {
        while (true) {
            tick();
            painter.repaint();

            if (!circle && !accepted) {
                listenForServerRequest();
            }
        }
    }

    private void render(Graphics g) {
        g.drawImage(board, boardX, boardY, null);
        // g.drawImage(board, 0, 0, null);
        if (unableToCommunicateWithOpponent) {
            g.setColor(Color.RED);
            g.setFont(smallerFont);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int stringWidth = g2.getFontMetrics().stringWidth(unableToCommunicateWithOpponentString);
            g.drawString(unableToCommunicateWithOpponentString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
            return;
        }

        if (accepted) {
            for (int i = 0; i < spaces.length; i++) {
                if (spaces[i] != null) {
                    if (spaces[i].equals("X")) {
                        if (circle) {
                            drawRedX(g, i);
                        } else {
                            drawBlueX(g, i);
                        }
                    } else if (spaces[i].equals("O")) {
                        if (circle) {
                            drawBlueCircle(g, i);
                        } else {
                            drawRedCircle(g, i);
                        }
                    }
                }
            }
            if (won || enemyWon) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setStroke(new BasicStroke(10));
                g.setColor(Color.BLACK);
                drawWinningLine(g, g2);
                drawEndGameMessage(g, g2);
            }

            if (tie) {
                Graphics2D g2 = (Graphics2D) g;
                g.setColor(Color.BLACK);
                drawTieMessage(g, g2);
            }
        } else {
            drawWaitingMessage(g);
        }
    }

    private void drawRedX(Graphics g, int i) {
        g.drawImage(redX, boardX + (i % 3) * lengthOfSpace + 10 * (i % 3),
                boardY + (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
    }

    private void drawBlueX(Graphics g, int i) {
        g.drawImage(blueX, boardX + (i % 3) * lengthOfSpace + 10 * (i % 3),
                boardY + (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
    }

    private void drawRedCircle(Graphics g, int i) {
        g.drawImage(redCircle, boardX + (i % 3) * lengthOfSpace + 10 * (i % 3),
                boardY + (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
    }

    private void drawBlueCircle(Graphics g, int i) {
        g.drawImage(blueCircle, boardX + (i % 3) * lengthOfSpace + 10 * (i % 3),
                boardY + (int) (i / 3) * lengthOfSpace + 10 * (int) (i / 3), null);
    }

    // private void drawWinningLine(Graphics g, Graphics2D g2) {
    // int startX = boardX + firstSpot % 3 * lengthOfSpace + lengthOfSpace / 2;
    // int startY = boardY + (int) (firstSpot / 3) * lengthOfSpace + lengthOfSpace /
    // 2;
    // int endX = boardX + secondSpot % 3 * lengthOfSpace + lengthOfSpace / 2;
    // int endY = boardY + (int) (secondSpot / 3) * lengthOfSpace + lengthOfSpace /
    // 2;

    // g2.drawLine(startX, startY, endX, endY);
    // }

    private void drawWinningLine(Graphics g, Graphics2D g2) {
        int startX = boardX + firstSpot % 3 * lengthOfSpace + lengthOfSpace / 2;
        int startY = boardY + (int) (firstSpot / 3) * lengthOfSpace + lengthOfSpace / 2;
        int endX = boardX + secondSpot % 3 * lengthOfSpace + lengthOfSpace / 2;
        int endY = boardY + (int) (secondSpot / 3) * lengthOfSpace + lengthOfSpace / 2;

        int lineLength = 650; // Gjatesia e linjes, mund ta ndryshoni vleren sipas deshires
        int lengthOfSpace = 650;
        // Llogarit kendin dhe ben pershtatje ne rast se vija eshte diagonale
        double angle = Math.atan2(endY - startY, endX - startX);
        if (Math.abs(angle) == Math.PI / 4 || Math.abs(angle) == 3 * Math.PI / 4) {
            lineLength = (int) (lengthOfSpace * Math.sqrt(2)); // Pershtat gjatesine per diagonale
        }

        // Llogarit pikat e fillimit dhe mbarimit te vijes duke perdorur llogaritjet
        // trigonometrike
        startX += (int) (lineLength / 2 * Math.cos(angle));
        startY += (int) (lineLength / 2 * Math.sin(angle));
        endX -= (int) (lineLength / 2 * Math.cos(angle));
        endY -= (int) (lineLength / 2 * Math.sin(angle));

        g2.setStroke(new BasicStroke(10));
        g2.drawLine(startX, startY, endX, endY);
    }

    private void drawEndGameMessage(Graphics g, Graphics2D g2) {
        g.setColor(Color.RED);
        g.setFont(largerFont);
        String message = (won) ? wonString : enemyWonString;
        int stringWidth = g2.getFontMetrics().stringWidth(message);
        g.drawString(message, boardX + (WIDTH - stringWidth) / 2, boardY + HEIGHT / 2);
    }

    private void drawTieMessage(Graphics g, Graphics2D g2) {
        g.setFont(largerFont);
        int stringWidth = g2.getFontMetrics().stringWidth(tieString);
        g.drawString(tieString, boardX + (WIDTH - stringWidth) / 2, boardY + HEIGHT / 2);
    }

    private void drawWaitingMessage(Graphics g) {
        g.setColor(Color.RED);
        g.setFont(font);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int stringWidth = g2.getFontMetrics().stringWidth(waitingString);
        g.drawString(waitingString, boardX + (WIDTH - stringWidth) / 2, boardY + HEIGHT / 2);
    }

    private void startTimer() {
        if (!timerRunning) {
            timerRunning = true;
            Thread timerThread = new Thread(() -> {
                while (timerRunning) {
                    try {
                        Thread.sleep(1000);
                        if (yourTurn) {
                            player1Timer--;
                        } else {
                            player2Timer--;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            timerThread.start();
        }
    }

    private void resetTimerForCurrentPlayer() {
        if (circle) {
            player1Timer = 10;
        } else {
            player2Timer = 10;
        }
    }

    private void updateTimersForDisplay() {
        player1TimerDisplay = (yourTurn) ? player1Timer : player1Timer - 1;
        player2TimerDisplay = (!yourTurn) ? player2Timer : player2Timer - 1;
    }

    private void tick() {
        if (errors >= 10)
            unableToCommunicateWithOpponent = true;

        if (!yourTurn && !unableToCommunicateWithOpponent) {
            try {
                int space = dis.readInt();
                if (circle)
                    spaces[space] = "X";
                else
                    spaces[space] = "O";

                yourTurn = true;
                resetTimerForCurrentPlayer(); // Riazhoni timerin kur fillon radha e lojtarit
                timerExpired = false;
                checkForEnemyWin();
                checkForTie();

                // Nisni timerin kur merrni nje levizje nga kundershti
                startTimer();
            } catch (IOException e) {
                e.printStackTrace();
                errors++;
            }
        }

        // Kontrollo kohen per lojtarin aktual dhe nderprit lojen nese koha ka skaduar
        checkTimer();

        // Perditeso kohen e shfaqur ne klasen Painter
        updateTimersForDisplay();

        // Kontrollo per fitoren dhe barazimet
        checkForWin();
        checkForTie();
    }

    private void checkTimer() {
        if (timerExpired) {
            System.out.println("Koha ka skaduar!"); // Kontrollo ne console
            // Koha ka skaduar, nderprit lojen dhe kaloni tek lojtari tjeter
            switchTurns();
        } else if (yourTurn && player1Timer <= 0) {
            System.out.println("Koha ka skaduar per lojtarin 1!"); // Kontrollo ne console
            // Koha ka skaduar per lojtarin 1, nderprit lojen ose nderroni lojtarin
            timerExpired = true;
        } else if (!yourTurn && player2Timer <= 0) {
            System.out.println("Koha ka skaduar per lojtarin 2!"); // Kontrollo ne console
            // Koha ka skaduar per lojtarin 2, nderprit lojen ose nderroni lojtarin
            timerExpired = true;
        }
    }

    private void switchTurns() {
        System.out.println("Kalimi i radhes se lojtareve!"); // Kontrollo ne console
        yourTurn = !yourTurn;
        opponentTurn = !yourTurn; // Perditeso variablen e re
        System.out.println("yourTurn aktual: " + yourTurn); // Shtoni kete printim
        System.out.println("opponentTurn aktual: " + opponentTurn); // Shtoni kete prinUSER tim
        resetTimerForCurrentPlayer(); // Riazhoni timerin per lojtarin aktual
        timerExpired = false;
        startTimer(); // Nisni timerin per lojtarin aktual
    }

    private void checkForWin() {
        for (int i = 0; i < wins.length; i++) {
            if (circle) {
                if (spaces[wins[i][0]] == "O" && spaces[wins[i][1]] == "O" && spaces[wins[i][2]] == "O") {
                    firstSpot = wins[i][0];
                    secondSpot = wins[i][2];
                    won = true;
                }
            } else {
                if (spaces[wins[i][0]] == "X" && spaces[wins[i][1]] == "X" && spaces[wins[i][2]] == "X") {
                    firstSpot = wins[i][0];
                    secondSpot = wins[i][2];
                    won = true;
                }
            }
        }
    }

    private void checkForEnemyWin() {
        for (int i = 0; i < wins.length; i++) {
            if (circle) {
                if (spaces[wins[i][0]] == "X" && spaces[wins[i][1]] == "X" && spaces[wins[i][2]] == "X") {
                    firstSpot = wins[i][0];
                    secondSpot = wins[i][2];
                    enemyWon = true;
                }
            } else {
                if (spaces[wins[i][0]] == "O" && spaces[wins[i][1]] == "O" && spaces[wins[i][2]] == "O") {
                    firstSpot = wins[i][0];
                    secondSpot = wins[i][2];
                    enemyWon = true;
                }
            }
        }
    }

    private void checkForTie() {
        // Check for a win first before checking for a tie
        checkForWin();

        // If neither player has won and all spaces are filled, set tie to true
        if (!won && !enemyWon) {
            for (int i = 0; i < spaces.length; i++) {
                if (spaces[i] == null) {
                    return;
                }
            }
            tie = true;
        }
    }

    private void listenForServerRequest() {
        Socket socket = null;
        try {
            socket = serverSocket.accept();
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            accepted = true;
            System.out.println("CLIENT HAS REQUESTED TO JOIN, AND WE HAVE ACCEPTED");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean connect() {
        try {
            socket = new Socket(ip, port);
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            accepted = true;
        } catch (IOException e) {
            System.out.println("Unable to connect to the address: " + ip + ":" + port + " | Starting a server");
            return false;
        }
        System.out.println("Successfully connected to the server.");
        return true;
    }

    private void initializeServer() {
        try {
            serverSocket = new ServerSocket(port, 8, InetAddress.getByName(ip));
        } catch (Exception e) {
            e.printStackTrace();
        }
        yourTurn = true;
        circle = false;
    }

    private void loadImages() {
        try {
            board = ImageIO.read(getClass().getResourceAsStream("/img/board.png"));
            redX = ImageIO.read(getClass().getResourceAsStream("/img/redX.png"));
            redCircle = ImageIO.read(getClass().getResourceAsStream("/img/redCircle.png"));
            blueX = ImageIO.read(getClass().getResourceAsStream("/img/blueX.png"));
            blueCircle = ImageIO.read(getClass().getResourceAsStream("/img/blueCircle.png"));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        TicTacToe ticTacToe = new TicTacToe();
    }

    private class Painter extends JPanel implements MouseListener {
        private static final long serialVersionUID = 1L;
        private JLabel usernameLabel;

        public Painter() {
            setFocusable(true);
            requestFocus();
            setBackground(Color.WHITE);
            addMouseListener(this);
            usernameLabel = new JLabel("Username: " + username);
            usernameLabel.setFont(new Font("Verdana", Font.BOLD, 24));
            usernameLabel.setForeground(Color.BLACK);
            add(usernameLabel);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            render(g);

            // Shfaq kohen e lojtarit aktual
            g.setColor(Color.BLACK);
            g.setFont(new Font("Verdana", Font.BOLD, 18));
            g.setColor(Color.RED);
            String timerDisplay = (yourTurn) ? "Your Time: " + player1TimerDisplay + "s"
                    : "Opponent's Time: " + player2TimerDisplay + "s";
            g.drawString(timerDisplay, 180, 50);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (accepted) {
                if (yourTurn && !unableToCommunicateWithOpponent && !won && !enemyWon) {
                    int x = (e.getX() - boardX) / lengthOfSpace;
                    int y = (e.getY() - boardY) / lengthOfSpace;
                    y *= 3;
                    int position = x + y;

                    if (spaces[position] == null) {
                        if (!circle)
                            spaces[position] = "X";
                        else
                            spaces[position] = "O";
                        yourTurn = false;
                        repaint();
                        Toolkit.getDefaultToolkit().sync();

                        try {
                            dos.writeInt(position);
                            dos.flush();
                        } catch (IOException e1) {
                            errors++;
                            e1.printStackTrace();
                        }

                        System.out.println("DATA WAS SENT");
                        checkForWin();
                        checkForTie();
                        usernameLabel.setText("Username: " + username);
                    }
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {

        }

        @Override
        public void mouseReleased(MouseEvent e) {

        }

        @Override
        public void mouseEntered(MouseEvent e) {

        }

        @Override
        public void mouseExited(MouseEvent e) {

        }
    }

}