package br.com.farmaetiquetas.farma_api.Service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FreteService {

    private final String API_KEY = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImYwNWE1NDExYTYyOTQ1NGFhNzQ3OWUwOTM0MTAxZTUxIiwiaCI6Im11cm11cjY0In0=";

    // Coordenadas EXATAS da Farmácia Modelo (Setor Oeste)
    private final double LOJA_LAT = -16.680033987941297;
    private final double LOJA_LON = -49.26735445092394;

    public Map<String, Object> calcularFrete(String cepString, boolean isCheckboxMarcada) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("distancia", 0.0);
        resultado.put("valor", null);

        try {
            System.out.println("\n=========================================");
            System.out.println("-> INICIANDO CÁLCULO PARA O CEP: " + cepString);

            JSONObject dadosEndereco = buscarEndereco(cepString);
            if (dadosEndereco.isEmpty()) {
                System.out.println("-> ERRO FATAL: Nenhuma API de CEP encontrou esse endereço. Abortando.");
                return resultado;
            }

            String bairroNome = dadosEndereco.optString("bairro", "");
            String logradouro = dadosEndereco.optString("logradouro", "");
            String cidade = dadosEndereco.optString("localidade", "");
            String uf = dadosEndereco.optString("uf", "");

            Double distanciaEscolhida = null;

            // 1. TENTA USAR O GPS DIRETO
            if (dadosEndereco.has("lat") && dadosEndereco.has("lon")) {
                try {
                    double clienteLat = dadosEndereco.getDouble("lat");
                    double clienteLon = dadosEndereco.getDouble("lon");
                    distanciaEscolhida = obterDistanciaRodoviaria(LOJA_LAT, LOJA_LON, clienteLat, clienteLon);
                } catch (Exception e) {
                    System.out.println("-> Falha na rota do GPS. Indo para o Mapa...");
                }
            }

            // 2. FALLBACK PARA O MAPA
            if (distanciaEscolhida == null) {
                String bairroLimpo = limparNomeBairro(bairroNome);
                List<JSONObject> coordenadasPossiveis = buscarCoordenadasPeloEndereco(logradouro, bairroLimpo, cidade, uf);

                if (coordenadasPossiveis != null && !coordenadasPossiveis.isEmpty()) {
                    for (JSONObject coord : coordenadasPossiveis) {
                        try {
                            double clienteLat = coord.getDouble("lat");
                            double clienteLon = coord.getDouble("lon");
                            distanciaEscolhida = obterDistanciaRodoviaria(LOJA_LAT, LOJA_LON, clienteLat, clienteLon);
                            break;
                        } catch (Exception e) {}
                    }
                }
            }

            if (distanciaEscolhida == null) {
                System.out.println("-> ERRO: O mapa não conseguiu achar rota.");
                return resultado;
            }

            double distanciaArredondada = Math.round(distanciaEscolhida * 10.0) / 10.0;
            resultado.put("distancia", distanciaArredondada);

            System.out.println("---------- DEBUG DE COBRANÇA ----------");
            System.out.println("CEP: " + cepString + " | KM: " + distanciaArredondada + " | Bairro: " + bairroNome);

            // =========================================================
            // --- REGRAS DE COBRANÇA (CORRIGIDAS) ---
            // =========================================================

            if (distanciaArredondada > 15.0) return resultado;

            // REGRA 1: Convênio (Gratuidade)
            if (isCheckboxMarcada && isCepDeConvenio(cepString)) {
                resultado.put("valor", 0.0);
                return resultado;
            }

            // REGRA 2: Proteção de Distância (Se for longe, ignora valor fixo de bairro)
            // Isso corrige o erro do CEP 74375210 que dava 5 reais mesmo sendo longe
            if (distanciaArredondada > 5.0) {
                if (distanciaArredondada <= 8.0) resultado.put("valor", 12.00);
                else if (distanciaArredondada <= 12.0) resultado.put("valor", 15.00);
                else resultado.put("valor", 20.00);

                System.out.println("-> Cobrança por KM aplicada (Distância > 5km)");
                return resultado;
            }

            // REGRA 3: Bairros com Taxa Fixa (Apenas se for perto, <= 5km)
            String bairroUpper = bairroNome.toUpperCase();
            boolean isBairroFixo = bairroUpper.contains("SETOR MARISTA") ||
                    bairroUpper.contains("CENTRO") ||
                    bairroUpper.contains("SETOR CENTRAL") ||
                    bairroUpper.contains("SETOR AEROPORTO") ||
                    bairroUpper.contains("SETOR OESTE");

            if (isBairroFixo) {
                resultado.put("valor", 5.00);
                System.out.println("-> Taxa fixa de Bairro aplicada");
                return resultado;
            }

            // REGRA 4: Tabela Geral para distâncias curtas
            if (distanciaArredondada <= 2.0) resultado.put("valor", 5.00);
            else if (distanciaArredondada <= 3.5) resultado.put("valor", 8.00);
            else resultado.put("valor", 10.00); // Faixa de 3.6km até 5.0km

            return resultado;

        } catch (Exception e) {
            System.out.println("-> ERRO: " + e.getMessage());
            return resultado;
        }
    }

    private String limparNomeBairro(String bairro) {
        if (bairro == null) return "";
        String limpo = bairro.replaceAll("(?i)\\d+[ªaºo]?\\s*(Etapa|Fase)", "");
        limpo = limpo.replaceAll("(?i)(Residencial|Condomínio|Loteamento)", "");
        return limpo.trim();
    }

    private boolean isCepDeConvenio(String cepString) {
        String cepLimpo = cepString.replaceAll("\\D", "");
        if (cepLimpo.isEmpty()) return false;
        long cep = Long.parseLong(cepLimpo);
        // Lista de CEPs de Convênio mantida conforme sua regra original
        return (cep == 74070100) || (cep == 74075210) || (cep >= 74080010 && cep <= 74093350) ||
                (cep == 74093210) || (cep == 74110090) || (cep == 74110130) || (cep == 74115030) ||
                (cep == 74115060) || (cep == 74150150) || (cep == 74351003) || (cep == 74672020);
    }

    public JSONObject buscarEndereco(String cep) {
        String cepLimpo = cep.replaceAll("\\D", "");
        JSONObject jsonConvertido = new JSONObject();

        try {
            URL url = new URL("https://cep.awesomeapi.com.br/json/" + cepLimpo);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) content.append(inputLine);
                in.close();

                JSONObject jsonApi = new JSONObject(content.toString());
                jsonConvertido.put("logradouro", jsonApi.optString("address", ""));
                jsonConvertido.put("bairro", jsonApi.optString("district", ""));
                jsonConvertido.put("localidade", jsonApi.optString("city", ""));
                jsonConvertido.put("uf", jsonApi.optString("state", ""));

                if (jsonApi.has("lat") && jsonApi.has("lng") && !jsonApi.getString("lat").isEmpty()) {
                    jsonConvertido.put("lon", Double.parseDouble(jsonApi.getString("lng")));
                    jsonConvertido.put("lat", Double.parseDouble(jsonApi.getString("lat")));
                }
                return jsonConvertido;
            }
        } catch (Exception e) {}

        try {
            URL url = new URL("https://viacep.com.br/ws/" + cepLimpo + "/json/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) content.append(inputLine);
                in.close();

                JSONObject jsonApi = new JSONObject(content.toString());
                if (!jsonApi.has("erro")) {
                    jsonConvertido.put("logradouro", jsonApi.optString("logradouro", ""));
                    jsonConvertido.put("bairro", jsonApi.optString("bairro", ""));
                    jsonConvertido.put("localidade", jsonApi.optString("localidade", ""));
                    jsonConvertido.put("uf", jsonApi.optString("uf", ""));
                    return jsonConvertido;
                }
            }
        } catch (Exception e) {}

        return jsonConvertido;
    }

    private List<JSONObject> buscarCoordenadasPeloEndereco(String logradouro, String bairro, String cidade, String uf) throws Exception {
        String query = logradouro + ", " + bairro + ", " + cidade + ", " + uf + ", Brazil";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
        String urlStr = "https://api.openrouteservice.org/geocode/search?api_key=" + API_KEY + "&text=" + encodedQuery + "&size=3";
        JSONObject response = fazerRequisicaoHttp(urlStr);
        return extrairCoordenadasJson(response);
    }

    private List<JSONObject> extrairCoordenadasJson(JSONObject response) {
        List<JSONObject> locais = new ArrayList<>();
        if (response.has("features")) {
            JSONArray features = response.getJSONArray("features");
            for (int i = 0; i < features.length(); i++) {
                JSONArray coords = features.getJSONObject(i).getJSONObject("geometry").getJSONArray("coordinates");
                JSONObject res = new JSONObject();
                res.put("lon", coords.getDouble(0));
                res.put("lat", coords.getDouble(1));
                locais.add(res);
            }
        }
        return locais;
    }

    private double obterDistanciaRodoviaria(double lat1, double lon1, double lat2, double lon2) throws Exception {
        String urlStr = "https://api.openrouteservice.org/v2/directions/driving-car?api_key=" + API_KEY + "&start=" + lon1 + "," + lat1 + "&end=" + lon2 + "," + lat2;
        JSONObject response = fazerRequisicaoHttp(urlStr);
        if (response.has("features") && response.getJSONArray("features").length() > 0) {
            double distMetros = response.getJSONArray("features").getJSONObject(0).getJSONObject("properties").getJSONObject("summary").getDouble("distance");
            return distMetros / 1000.0;
        }
        throw new Exception("Erro de rota");
    }

    private JSONObject fazerRequisicaoHttp(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8");
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return new JSONObject(sb.toString());
    }
}