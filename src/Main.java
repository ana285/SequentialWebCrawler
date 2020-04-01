import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Main {

    public static void main(String[] args)
    {

        SequentialWebCrawler crawl = new SequentialWebCrawler("http://riweb.tibeica.com/crawl/");
        //SequentialWebCrawler crawl = new SequentialWebCrawler("https://moodle.dc.ac.tuiasi.ro/");
        try {
           crawl.crawl("data.json");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
