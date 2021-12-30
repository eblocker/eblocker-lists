/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.lists.tls;

import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.lists.util.HttpClient;
import org.eblocker.lists.util.HttpClientFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class IntermediateCertificateListCreator {
    private static final Logger log = LoggerFactory.getLogger(IntermediateCertificateListCreator.class);
    public static final String CSV_COLUMN_CERT_NAME = "Certificate Name";
    public static final String CSV_COLUMN_COMMENTS = "Comments";
    public static final String CSV_COLUMN_PEM = "PEM Info";

    private final String url;
    private final String outputFile;
    private final int countMinimum;
    private final int countMaximum;
    private final HttpClient httpClient;

    public static void main(String[] args) throws IntermediateCertificateException {
        try {
            Properties properties = new Properties();
            properties.load(ClassLoader.getSystemResourceAsStream("tls.properties"));
            String url = properties.getProperty("intermediateCertificates.url");
            String outputFile = properties.getProperty("intermediateCertificates.outputFile");
            int countMinimum = Integer.valueOf(properties.getProperty("intermediateCertificates.count.minimum"));
            int countMaximum = Integer.valueOf(properties.getProperty("intermediateCertificates.count.maximum"));
            HttpClient httpClient = HttpClientFactory.create();
            new IntermediateCertificateListCreator(url, outputFile, countMinimum, countMaximum, httpClient).run();
        } catch (IOException e) {
            throw new IntermediateCertificateException("failed to load properties", e);
        }
    }

    public IntermediateCertificateListCreator(String url, String outputFile, int countMinimum, int countMaximum, HttpClient httpClient) {
        this.url = url;
        this.outputFile = outputFile;
        this.countMinimum = countMinimum;
        this.countMaximum = countMaximum;
        this.httpClient = httpClient;
    }

    public void run() throws IntermediateCertificateException {
        try {
            X509Certificate[] certificates = downloadCertificates();
            validateCertificates(certificates);
            writeCertificates(certificates);
        } catch (CryptoException e) {
            throw new IntermediateCertificateException("failed to store certificates", e);
        } catch (IOException e) {
            throw new IntermediateCertificateException("i/o failed", e);
        }
    }

    private X509Certificate[] downloadCertificates() throws IOException {
        try (Reader reader = new InputStreamReader(httpClient.download(url, null, null))) {
            CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader());
            List<CSVRecord> records = parser.getRecords();
            return records.stream()
                .map(this::parseRecord)
                .filter(Objects::nonNull)
                .toArray(X509Certificate[]::new);
        }
    }

    private X509Certificate parseRecord(CSVRecord record) {
        try {
            log.debug("parsing {}", record.get(CSV_COLUMN_CERT_NAME));
            String certificate = normalizePem(record.get(CSV_COLUMN_PEM));
            ByteArrayInputStream in = new ByteArrayInputStream(certificate.getBytes());
            return PKI.loadCertificate(in);
        } catch (CryptoException e) {
            log.warn("ignoring unparseable certificate {}", record.get(CSV_COLUMN_CERT_NAME), e.getMessage());
            log.warn("comment: {}", record.get(CSV_COLUMN_COMMENTS));
            log.trace("full error", e);
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String normalizePem(String pem) {
        StringBuilder result = new StringBuilder();
        String[] lines = pem.replaceAll("'", "").split("\n");
        for(String line : lines) {
            result.append(line.trim());
            result.append('\n');
        }
        return result.toString();
    }

    private void validateCertificates(X509Certificate[] certificates) throws IntermediateCertificateException {
        if (certificates.length < countMinimum) {
            log.error("expected at least {} certificates but got {}", countMinimum, certificates.length);
            throw new IntermediateCertificateException("too few certificates: " + certificates.length);
        }
        if (certificates.length > countMaximum) {
            log.error("expected at most {} certificates but got {}", countMaximum, certificates.length);
            throw new IntermediateCertificateException("too many certificates: " + certificates.length);
        }
    }

    private void writeCertificates(X509Certificate[] certificates) throws IOException, CryptoException {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            PKI.storeCertificates(certificates, out);
            log.info("wrote {} intermediate certificates to {}", certificates.length, outputFile);
        }
    }

    public static class IntermediateCertificateException extends Exception {
        IntermediateCertificateException(String message) {
            super(message);
        }

        IntermediateCertificateException(String message, Throwable cause) {
            super(message, cause);
        }
    }


}
