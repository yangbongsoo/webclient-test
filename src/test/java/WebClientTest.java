import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

public class WebClientTest {

	private static final Logger logger = LoggerFactory.getLogger(WebClientTest.class);

	CountDownLatch countDownLatch;
	TcpClient tcpClient;

	@Before
	public void setUp() throws Exception {
		countDownLatch = new CountDownLatch(10);
		ConnectionProvider provider = ConnectionProvider.elastic("pool-1", Duration.ofMillis(10000));
		tcpClient = TcpClient.create(provider)
			.port(80)
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000)
			.doOnConnected(
				c -> c.addHandlerLast(new ReadTimeoutHandler(5))
					.addHandlerLast(new WriteTimeoutHandler(5)));
	}

	@Test // wiretap enable
	public void connectionResetByPeer() throws Exception {
		ReactorClientHttpConnector connector = new ReactorClientHttpConnector(
			HttpClient.from(tcpClient).compress(true).wiretap(true));
		WebClient.builder()
			.clientConnector(connector)
			.baseUrl("http://localhost:8001") // StableServer
			.build()
			.get()
			.exchange()
			.flatMap(res -> res.toEntity(String.class))
			.doOnSuccess(result -> {
				logger.info("[WebClientTest] success: {}", result.getBody());
			})
			.doOnError(result -> {
				logger.warn("[WebClientTest] error", result);
			})
			.subscribe(System.out::println);

		countDownLatch.await(5, TimeUnit.SECONDS);
	}

	@Test // wiretap disable
	public void noError() throws Exception {
		ReactorClientHttpConnector connector = new ReactorClientHttpConnector(
			HttpClient.from(tcpClient).compress(true).wiretap(false));

		WebClient.builder()
			.clientConnector(connector)
			.baseUrl("http://localhost:8001") // StableServer
			.build()
			.get()
			.exchange()
			.flatMap(res -> res.toEntity(String.class))
			.doOnSuccess(result -> {
				logger.info("[WebClientTest] success: {}", result.getBody());
			})
			.doOnError(result -> {
				logger.warn("[WebClientTest] error", result);
			})
			.subscribe(System.out::println);

		countDownLatch.await(5, TimeUnit.SECONDS);
	}
}
