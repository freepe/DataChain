package com.blockchainua.datachain;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.vertx.core.Vertx;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * Created by et on 18.03.2017.
 */
public class App {

	private void launch() {
//		Vertx.vertx().createHttpServer().requestHandler(req -> req.response().end("Hello World!")).listen(8080);
		Vertx.vertx().deployVerticle(new DatachainService(), stringAsyncResult -> {
			System.out.println("DatachainService has been started");
		});

		Scanner scanner = new Scanner(System.in);

		loop:
		while (true) {
			String input = scanner.nextLine();
			String[] tokens = input.split("\\s+");

			if (tokens.length == 0) {
				System.out.println("Type a command");
			} else {
				switch (tokens[0]) {
					case "exit":
						break loop;

					case "ipfs_init":
						ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
						try {
							ipfs.refs.local();
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							if (ipfs != null) {
								System.out.print("IPFS has been inited");
							}
						}
						break;

					case "ipfs_put":
						try {
							NamedStreamable.FileWrapper file = new NamedStreamable.FileWrapper(new File("hello.txt"));
							MerkleNode addResult = ipfs.add(file);
							String report = addResult.toJSONString();
							System.out.println("Result: " + report);

						} catch (IOException e) {
							e.printStackTrace();
						}

						break;

					default:
						System.out.println("Unknown command: " + tokens[0]);
				}
			}
		}

		scanner.close();
	}

	public static void main(String[] args) {
		App app = new App();
		app.launch();
	}

	private IPFS ipfs;
}
