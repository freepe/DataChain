package com.blockchainua.datachain;

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.io.Resources;
import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import rx.Subscription;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by et on 18.03.2017.
 */
public class DatachainService extends AbstractVerticle {

	@Override
	public void start() {
		setUpInitialData();

		web3 = Web3j.build(new HttpService());  // defaults to http://localhost:8545/
		subscription = web3.transactionObservable().subscribe(tx -> {
			String txLog = MoreObjects.toStringHelper(Transaction.class)
					.add("hash", tx.getHash())
					.add("from", tx.getFrom())
					.add("to", tx.getTo())
					.add("value", tx.getValue())
					.toString();

//			System.out.println("Log: tx: " + txLog);

			BigDecimal expectedValue = Convert.toWei(BigDecimal.valueOf(0.1), Convert.Unit.ETHER);
			if (addressTo.equalsIgnoreCase(tx.getTo()) /*&& expectedValue.equals(tx.getValue())*/) {
				if (paid.get()) {
					paid2.set(true);
				} else {
					paid.set(true);
				}

				System.out.println("Log: tx: " + tx.getHash());
			}
		});

		ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
		try {
			ipfs.refs.local();
		} catch (IOException e) {
			e.printStackTrace();
		}

		Router router = Router.router(vertx);

		router.route().handler(BodyHandler.create().setUploadsDirectory("uploads"));
		router.route().handler(LoggerHandler.create());
//		router.get("/").handler(this::index);
		router.route("/").handler(StaticHandler.create("assets"));
		router.route("/assets/*").handler(StaticHandler.create("assets"));
		router.post("/form").handler(this::handleForm);
		router.post("/rest/api/fff/").handler(this::handleGetEthAddress);
		router.get("/rest/api/get_paid").handler(this::handleGetPaid);
		router.get("/file/").handler(this::handleGetFile);
		router.get("/rest/api/get_paid2").handler(this::handleGetPaid2);

		router.get("/rest/api/products/:productID").handler(this::handleGetProduct);
		router.put("/rest/api/products/:productID").handler(this::handleAddProduct);
		router.get("/rest/api/products").handler(this::handleListProducts);

		vertx.createHttpServer().requestHandler(router::accept).listen(8082);
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		if (subscription != null) {
			subscription.unsubscribe();
		}
	}

	private void handleGetFile(RoutingContext routingContext) {
		String fileLink = routingContext.request().getParam("file_link");
		System.out.println("Log: handleGetFile: " + fileLink);

		HttpServerResponse response = routingContext.response();
		if (fileLink == null) {
			sendError(400, response);
		} else {
			String fileIPFSHash = filesLinks.get(fileLink);
			if (fileIPFSHash == null) {
				sendError(404, response);
			} else {
				String fileHtmlContent = "";

				try {
					URL url = Resources.getResource("assets/file.html");
					fileHtmlContent = Resources.toString(url, Charsets.UTF_8);
				} catch (IOException e) {
					e.printStackTrace();
				}

				fileHtmlContent = fileHtmlContent
						.replace("%pay_address%", addressTo)
//						.replace("%file_link%", "<a href='http://localhost:8080/ipfs/" + fileIPFSHash + "'>" + fileIPFSHash + "</a>");
						.replace("%file_link%", "http://localhost:8080/ipfs/" + fileIPFSHash);

				response
						.putHeader("content-type", "text/html")
						.end(fileHtmlContent);
			}
		}
	}

	private void handleGetPaid(RoutingContext routingContext) {
//		JsonObject jsonObject = routingContext.getBodyAsJson();
		System.out.println("Log: request body: " + routingContext.getBody().toString());

		HttpServerResponse response = routingContext.response();
		response
				.putHeader("content-type", "text/plain")
				.end(String.valueOf(paid.get()));
	}

	private void handleGetPaid2(RoutingContext routingContext) {
//		JsonObject jsonObject = routingContext.getBodyAsJson();
		System.out.println("Log: request body: " + routingContext.getBody().toString());

		HttpServerResponse response = routingContext.response();
		response
				.putHeader("content-type", "text/plain")
				.end(String.valueOf(paid2.get()));
	}

	private void handleGetEthAddress(RoutingContext routingContext) {


		String fileSize = routingContext.request().getParam("file_size");
		JsonObject jsonObject = routingContext.getBodyAsJson();
		System.out.println("Log: request body: " + jsonObject.encodePrettily());

		HttpServerResponse response = routingContext.response();
		response
				.putHeader("content-type", "application/json")
				.end(addressTo);

//				.end(new JsonObject()
//						.put("name", "Kitty")
//						.put("salary", fileSize)
//						.encodePrettily());
	}

	private void index(RoutingContext routingContext) {
		HttpServerResponse response = routingContext.response();
		response.putHeader("content-type", "text/html");
		response.end("Hello Kitty!");
	}

	private void handleForm(RoutingContext routingContext) {
		routingContext.response().putHeader("Content-Type", "text/html");
		routingContext.response().setChunked(true);

		for (FileUpload f : routingContext.fileUploads()) {
			System.out.println("f");
			String uuid = buildUUID();

			try {
				NamedStreamable.FileWrapper file = new NamedStreamable.FileWrapper(new File(f.uploadedFileName()));
				MerkleNode addResult = ipfs.add(file);

				filesLinks.put(uuid, addResult.hash.toBase58());

				String report = addResult.toJSONString();
				System.out.println("Result: " + report);

			} catch (IOException e) {
				e.printStackTrace();
			}

			routingContext.response().write("<p>Filename: " + f.fileName() + "</p>");
			routingContext.response().write("\n");
			routingContext.response().write("<p>Size: " + f.size() + "</p>");
			routingContext.response().write("\n");
			routingContext.response().write("<a href='http://localhost:8082/file/?file_link=" + uuid + "'>" + uuid + "</a>");
			routingContext.response().write("\n");

		}

		routingContext.response().end();
	}

	private void handleGetProduct(RoutingContext routingContext) {
		String productID = routingContext.request().getParam("productID");
		HttpServerResponse response = routingContext.response();
		if (productID == null) {
			sendError(400, response);
		} else {
			JsonObject product = products.get(productID);
			if (product == null) {
				sendError(404, response);
			} else {
				response.putHeader("content-type", "application/json").end(product.encodePrettily());
			}
		}
	}

	private void handleAddProduct(RoutingContext routingContext) {
		String productID = routingContext.request().getParam("productID");
		HttpServerResponse response = routingContext.response();
		if (productID == null) {
			sendError(400, response);
		} else {
			JsonObject product = routingContext.getBodyAsJson();
			if (product == null) {
				sendError(400, response);
			} else {
				products.put(productID, product);
				response.end();
			}
		}
	}

	private void handleListProducts(RoutingContext routingContext) {
		JsonArray arr = new JsonArray();
		products.forEach((k, v) -> arr.add(v));
		routingContext.response().putHeader("content-type", "application/json").end(arr.encodePrettily());
	}

	private void sendError(int statusCode, HttpServerResponse response) {
		response.setStatusCode(statusCode).end();
	}

	private void setUpInitialData() {
		addProduct(new JsonObject().put("id", "1").put("name", "Egg Whisk").put("price", 3.99).put("weight", 150));
		addProduct(new JsonObject().put("id", "2").put("name", "Tea Cosy").put("price", 5.99).put("weight", 100));
		addProduct(new JsonObject().put("id", "3").put("name", "Spatula").put("price", 1.00).put("weight", 80));
	}

	private void addProduct(JsonObject product) {
		products.put(product.getString("id"), product);
	}

	private String buildUUID() {
		return UUID.randomUUID().toString();
	}

	private Map<String, JsonObject> products = new HashMap<>();
	private Web3j web3;
	private Subscription subscription;
	private String addressTo = "0xBBEB98414B408ca6bfBfC7707596520E10C14f64";
	private AtomicBoolean paid = new AtomicBoolean(false);
	private AtomicBoolean paid2 = new AtomicBoolean(false);
	private Map<String, String> filesLinks = new HashMap<>();
	private IPFS ipfs;

	{
		filesLinks.put("fff", "ggg");
	}
}
