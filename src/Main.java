import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

public class Main {

    private static final List<Capital> CAPITAIS = List.of(
        new Capital("São Paulo", -23.5505, -46.6333),
        new Capital("Aracaju ", -10.9167 , -37.05),
        new Capital("Belém", -1.4558, -48.5039),
        new Capital("Belo Horizonte", -19.9167, -43.9333),
        new Capital("Boa Vista", 2.81972, -60.67333),
        new Capital("Brasília", -15.7939, -47.882),
        new Capital("Campo Grande", -20.44278, -54.64639),
        new Capital("Cuiabá", -15.5989, -56.0949),
        new Capital("Curitiba", -25.4297, -49.2711),
        new Capital("Florianópolis", -27.5935, -48.55854),
        new Capital("Fortaleza", -3.7275, -38.5275),
        new Capital("Goiânia", -16.6667, -49.25),
        new Capital("João Pessoa", -7.12, -34.88),
        new Capital("Macapá", 0.033, -51.05),
        new Capital("Maceió", -9.66583, -35.73528),
        new Capital("Manaus", -3.1189, -60.0217),
        new Capital("Natal", -5.7833, -35.2),
        new Capital("Palmas", -10.16745, -48.32766),
        new Capital("Porto Alegre", -30.0331, -51.23),
        new Capital("Porto Velho", -8.76194, -63.90389),
        new Capital("Recife", -8.05, -34.9),
        new Capital("Rio Branco", -9.97472, -67.81),
        new Capital("Rio de Janeiro", -22.9111, -43.2056),
        new Capital("Salvador", -12.9747, -38.4767),
        new Capital("São Luís", -2.5283, -44.3044),
        new Capital("Teresina", -5.08917, -42.80194),
        new Capital("Vitória", -20.2889, -40.3083)
        
    );

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Tempo médio (sem threads): " + executarExperimento(1) + " segundos");
        System.out.println("Tempo médio (3 threads): " + executarExperimento(3) + " segundos");
        System.out.println("Tempo médio (9 threads): " + executarExperimento(9) + " segundos");
        System.out.println("Tempo médio (27 threads): " + executarExperimento(27) + " segundos");
    }

    private static double executarExperimento(int nThreads) throws InterruptedException {
        List<Double> tempos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Instant inicio = Instant.now();
            ExecutorService executor = Executors.newFixedThreadPool(nThreads);
            List<List<Capital>> partes = dividirCapitais(nThreads);
            List<Resultado> resultados = new ArrayList<>();

            for (List<Capital> parte : partes) {
                executor.execute(() -> {
                    for (Capital capital : parte) {
                        try {
                            resultados.add(obterDadosClimaticos(capital));
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);

            for (Resultado resultado : resultados) {
                exibirResultados(resultado);
            }

            Instant fim = Instant.now();
            tempos.add(Duration.between(inicio, fim).toMillis() / 1000.0);
        }
        return calcularTempoMedio(tempos);
    }

    private static List<List<Capital>> dividirCapitais(int nThreads) {
        int tamanhoParte = (int) Math.ceil((double) CAPITAIS.size() / nThreads);
        List<List<Capital>> partes = new ArrayList<>();
        for (int i = 0; i < CAPITAIS.size(); i += tamanhoParte) {
            partes.add(CAPITAIS.subList(i, Math.min(CAPITAIS.size(), i + tamanhoParte)));
        }
        return partes;
    }

    private static Resultado obterDadosClimaticos(Capital capital) throws IOException, InterruptedException {
        String uri = String.format("https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&hourly=temperature_2m",
                capital.latitude, capital.longitude);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String body = response.body();
            return processarDados(body, capital.nome);
        } else {
            System.out.println("Erro na requisição para: " + capital.nome + " - Código: " + response.statusCode());
            return new Resultado(capital.nome, new HashMap<>());
        }
    }

    private static Resultado processarDados(String jsonResponse, String cidade) {
        Map<String, double[]> temperaturasPorDia = new HashMap<>();
        JSONObject json = new JSONObject(jsonResponse);

        if (!json.has("hourly")) {
            System.out.println("Dados 'hourly' não encontrados para: " + cidade);
            return new Resultado(cidade, temperaturasPorDia);
        }

        JSONArray temperatures = json.getJSONObject("hourly").getJSONArray("temperature_2m");
        JSONArray times = json.getJSONObject("hourly").getJSONArray("time");

        for (int i = 0; i < temperatures.length(); i++) {
            String date = times.getString(i).substring(0, 10);
            if (!temperatures.isNull(i)) {
                double temp = temperatures.getDouble(i);
                temperaturasPorDia.computeIfAbsent(date, k -> new double[3]); // [min, max, soma]
                double[] stats = temperaturasPorDia.get(date);

                if (stats[0] == 0 || temp < stats[0]) stats[0] = temp;
                if (temp > stats[1]) stats[1] = temp;
                stats[2] += temp;
            }
        }

        for (String date : temperaturasPorDia.keySet()) {
            double[] stats = temperaturasPorDia.get(date);
            stats[2] /= 24; // média diária
        }

        return new Resultado(cidade, temperaturasPorDia);
    }

    private static void exibirResultados(Resultado resultado) {
        System.out.println("Cidade: " + resultado.cidade);
        for (Map.Entry<String, double[]> entry : resultado.dados.entrySet()) {
            String data = entry.getKey();
            double[] stats = entry.getValue();
            System.out.printf("Data: %s - Mínima: %.2f, Máxima: %.2f, Média: %.2f%n", data, stats[0], stats[1], stats[2]);
        }
    }

    private static double calcularTempoMedio(List<Double> tempos) {
        return tempos.stream().collect(Collectors.averagingDouble(Double::doubleValue));
    }

    static class Capital {
        String nome;
        double latitude;
        double longitude;

        Capital(String nome, double latitude, double longitude) {
            this.nome = nome;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    static class Resultado {
        String cidade;
        Map<String, double[]> dados;

        Resultado(String cidade, Map<String, double[]> dados) {
            this.cidade = cidade;
            this.dados = dados;
        }
    }
}
