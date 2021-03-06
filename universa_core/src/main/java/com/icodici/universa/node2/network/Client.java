/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Client {

    static {
        Config.forceInit(ItemResult.class);
        Config.forceInit(HashId.class);
        Config.forceInit(Contract.class);
    }

    protected interface Executor<T> {
        T execute() throws Exception;
    }

    final BasicHTTPClient client;

    public Client(String rootUrlString, PrivateKey clientPrivateKey,
                  PublicKey nodePublicKey) throws IOException {
        client = new BasicHTTPClient(rootUrlString);
        client.start(clientPrivateKey, nodePublicKey);
    }

    public Client(PrivateKey myPrivateKey, NodeInfo nodeInfo) throws IOException {
        client = new BasicHTTPClient(nodeInfo.publicUrlString());
        client.start(myPrivateKey, nodeInfo.getPublicKey());
    }

    public Client(String someNodeUrl, PrivateKey clientPrivateKey) throws IOException {
        loadNetworkFrom(someNodeUrl);
        NodeRecord r = Do.sample(nodes);
        System.out.println("Will try to connecto to the random node: " + r);
        client = new BasicHTTPClient(r.url);
        client.start(clientPrivateKey, r.key);
    }

    private class NodeRecord {
        public final String url;
        public final PublicKey key;

        private NodeRecord(Binder data) throws IOException {
            url = data.getStringOrThrow("url");
            try {
                key = new PublicKey(data.getBinaryOrThrow("key"));
            } catch (EncryptionError encryptionError) {
                throw new IOException("failed to construct node public key", encryptionError);
            }
        }

        @Override
        public String toString() {
            return "Node(" + url + "," + key + ")";
        }
    }

    private List<NodeRecord> nodes = new ArrayList<>();

    private void loadNetworkFrom(String someNodeUrl) throws IOException {
        URL url = new URL(someNodeUrl + "/network");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Universa JAVA API Client");
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200)
            throw new IOException("failed to access " + url + ", reponseCode " + connection.getResponseCode());

        Binder bres = Boss.unpack((Do.read(connection.getInputStream())))
                .getBinderOrThrow("response");
        nodes.clear();
        System.out.println("Remote node version: " + bres.getStringOrThrow("version"));
        for (Binder b : bres.getBinders("nodes"))
            nodes.add(new NodeRecord(b));
        System.out.println(nodes);
    }

    public ItemResult register(byte[] packed) throws ClientError {
        return protect(() -> (ItemResult) client.command("approve", "packedItem", packed)
                .get("itemResult"));
    }

    public final ItemResult getState(@NonNull Approvable item) throws ClientError {
        return getState(item.getId());
    }

    public ItemResult getState(HashId itemId) throws ClientError {
        return protect(() -> {
            return (ItemResult) client.command("getState",
                                               "itemId", itemId
            ).getOrThrow("itemResult");
        });
    }

    public Binder command(String name, Object... params) throws IOException {
        return client.command(name, params);
    }

    public BasicHTTPClient.Answer request(String name, Object... params) throws IOException {
        return client.request(name, params);
    }

    protected final <T> T protect(Executor<T> e) throws ClientError {
        try {
            return e.execute();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ClientError(ex);
        }
    }
}
