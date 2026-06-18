import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

class Venda {

    private final String cliente;
    private final double valor;

    Venda(String cliente, double valor) {
        this.cliente = cliente;
        this.valor = valor;
    }

    String getCliente() {
        return this.cliente;
    }

    double getValor() {
        return this.valor;
    }

    Map<String, Object> toDict() {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("cliente", this.cliente);
        dados.put("valor", this.valor);
        return dados;
    }

    static Venda fromDict(Map<String, Object> dados) {
        String cliente = (String) dados.get("cliente");

        double valor = ((Number) dados.get("valor")).doubleValue();
        return new Venda(cliente, valor);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Venda(cliente='%s', valor=%.2f)", this.cliente, this.valor);
    }
}

class Armazenamento {

    String nomeArquivo;

    Armazenamento(String nomeArquivo) {
        this.nomeArquivo = nomeArquivo;
    }

    protected List<Map<String, Object>> carregarJson() {
        File arquivo = new File(this.nomeArquivo);
        if (arquivo.exists()) {
            try {

                String conteudo = Files.readString(arquivo.toPath(), StandardCharsets.UTF_8);

                return JsonMini.parseListaDeObjetos(conteudo);
            } catch (Exception e) {

                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    protected void salvarJson(List<Map<String, Object>> dados) {

        String json = JsonMini.escreverListaDeObjetos(dados);
        try {

            Files.writeString(new File(this.nomeArquivo).toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Erro ao salvar o arquivo: " + e.getMessage());
        }
    }
}

class GerenciadorVendas extends Armazenamento {

    String mes;

    private List<Venda> vendas;

    GerenciadorVendas() {
        this(null);
    }

    GerenciadorVendas(String mes) {

        super("vendas_" + resolverMes(mes) + ".json");
        this.mes = resolverMes(mes);

        this.vendas = carregarVendas();
    }

    private static String resolverMes(String mes) {
        return (mes == null) ? YearMonth.now().toString() : mes;
    }

    private List<Venda> carregarVendas() {
        List<Map<String, Object>> dados = carregarJson();
        List<Venda> lista = new ArrayList<>();

        for (Map<String, Object> d : dados) {
            lista.add(Venda.fromDict(d));
        }
        return lista;
    }

    void salvar() {
        List<Map<String, Object>> dados = new ArrayList<>();
        for (Venda v : this.vendas) {
            dados.add(v.toDict());
        }
        salvarJson(dados);
    }

    void adicionar(String cliente, double valor) {
        this.vendas.add(new Venda(cliente, valor));
        salvar();
    }

    Venda apagar(int indice) {
        if (indice >= 0 && indice < this.vendas.size()) {
            Venda removida = this.vendas.remove(indice);
            salvar();
            return removida;
        }
        return null;
    }

    List<Venda> listar() {
        return this.vendas;
    }
}

abstract class Relatorio {

    static final double COMISSAO_PERCENTUAL = 0.03;

    static String formatarDinheiro(double valor) {

        String texto = String.format(Locale.US, "%,.2f", valor);

        texto = texto.replace(",", "X").replace(".", ",").replace("X", ".");
        return "R$ " + texto;
    }

    abstract void exibir();
}

class RelatorioMensal extends Relatorio {

    private final GerenciadorVendas gerenciador;

    RelatorioMensal(GerenciadorVendas gerenciador) {
        this.gerenciador = gerenciador;
    }

    @Override
    void exibir() {
        List<Venda> vendas = this.gerenciador.listar();

        if (vendas.isEmpty()) {
            System.out.println("\nNenhuma venda registrada neste período.");
            return;
        }

        double total = vendas.stream().mapToDouble(Venda::getValor).sum();
        int quantidade = vendas.size();
        double media = total / quantidade;

        double comissao = total * COMISSAO_PERCENTUAL;

        Map<String, Integer> frequencia = new LinkedHashMap<>();
        Map<String, Double> gastos = new LinkedHashMap<>();
        for (Venda v : vendas) {

            frequencia.merge(v.getCliente(), 1, Integer::sum);
            gastos.merge(v.getCliente(), v.getValor(), Double::sum);
        }

        String clienteMaisPedidos = chaveComMaiorValor(frequencia);
        String clienteMaisGastou = chaveComMaiorValor(gastos);

        double maiorVenda = vendas.stream().mapToDouble(Venda::getValor).max().getAsDouble();
        double menorVenda = vendas.stream().mapToDouble(Venda::getValor).min().getAsDouble();

        System.out.println("\n===== RELATÓRIO DE VENDAS =====");

        System.out.println("Total de vendas: " + formatarDinheiro(total));
        System.out.println("Quantidade de vendas: " + quantidade);
        System.out.println("Média de vendas: " + formatarDinheiro(media));
        System.out.println("Maior venda: " + formatarDinheiro(maiorVenda));
        System.out.println("Menor venda: " + formatarDinheiro(menorVenda));
        System.out.println("Cliente com mais pedidos: " + clienteMaisPedidos
                + " (" + frequencia.get(clienteMaisPedidos) + " pedidos)");
        System.out.println("Cliente que mais gastou: " + clienteMaisGastou
                + " (" + formatarDinheiro(gastos.get(clienteMaisGastou)) + ")");
        System.out.println("Comissão total do vendedor: " + formatarDinheiro(comissao));
    }

    private static <K> K chaveComMaiorValor(Map<K, ? extends Number> mapa) {
        K melhor = null;
        double maiorValor = Double.NEGATIVE_INFINITY;
        for (Map.Entry<K, ? extends Number> entrada : mapa.entrySet()) {
            if (entrada.getValue().doubleValue() > maiorValor) {
                maiorValor = entrada.getValue().doubleValue();
                melhor = entrada.getKey();
            }
        }
        return melhor;
    }
}

class RelatorioComparativo extends Relatorio {

    private final String mes1;
    private final String mes2;

    RelatorioComparativo(String mes1, String mes2) {
        this.mes1 = mes1;
        this.mes2 = mes2;
    }

    @Override
    void exibir() {

        GerenciadorVendas g1 = new GerenciadorVendas(this.mes1);
        GerenciadorVendas g2 = new GerenciadorVendas(this.mes2);

        double total1 = g1.listar().stream().mapToDouble(Venda::getValor).sum();
        double total2 = g2.listar().stream().mapToDouble(Venda::getValor).sum();

        System.out.println("\n===== COMPARAÇÃO DE MESES =====");
        System.out.println(this.mes1 + ": " + formatarDinheiro(total1));
        System.out.println(this.mes2 + ": " + formatarDinheiro(total2));

        if (total1 > total2) {
            System.out.println("O mês " + this.mes1 + " teve mais vendas!");
        } else if (total2 > total1) {
            System.out.println("O mês " + this.mes2 + " teve mais vendas!");
        } else {
            System.out.println("Ambos os meses tiveram o mesmo faturamento.");
        }
    }
}

public class SistemaVendas {

    private final GerenciadorVendas gerenciador;
    private final RelatorioMensal relatorio;

    private static final Scanner ENTRADA = new Scanner(System.in, StandardCharsets.UTF_8);

    SistemaVendas() {
        this.gerenciador = new GerenciadorVendas();
        this.relatorio = new RelatorioMensal(this.gerenciador);
    }

    private List<Venda> exibirLista() {
        List<Venda> vendas = this.gerenciador.listar();
        if (vendas.isEmpty()) {
            System.out.println("\nNenhuma venda registrada neste período.");
            return new ArrayList<>();
        }
        System.out.println("\n===== LISTA DE VENDAS =====");

        for (int i = 0; i < vendas.size(); i++) {
            Venda v = vendas.get(i);
            System.out.println((i + 1) + ". Cliente: " + v.getCliente()
                    + " - Valor: " + Relatorio.formatarDinheiro(v.getValor()));
        }
        return vendas;
    }

    private void opcaoAdicionar() {
        while (true) {
            System.out.print("Digite o nome do cliente (ou 'sair' para finalizar): ");
            String cliente = ENTRADA.nextLine().trim();
            if (cliente.equalsIgnoreCase("sair")) {
                break;
            }
            System.out.print("Digite o valor da venda para " + cliente + ": ");
            String textoValor = ENTRADA.nextLine().trim();
            try {

                double valor = Double.parseDouble(textoValor);
                if (valor >= 0) {
                    this.gerenciador.adicionar(cliente, valor);
                    System.out.println("Venda adicionada!");
                } else {
                    System.out.println("O valor deve ser positivo.");
                }
            } catch (NumberFormatException e) {

                System.out.println("Entrada inválida. Digite um número válido.");
            }
        }
    }

    private void opcaoApagar() {
        List<Venda> vendas = exibirLista();
        if (vendas.isEmpty()) {
            return;
        }
        System.out.print("\nDigite o número da venda que deseja apagar: ");
        try {

            int indice = Integer.parseInt(ENTRADA.nextLine().trim()) - 1;
            Venda removida = this.gerenciador.apagar(indice);
            if (removida != null) {
                System.out.println("\nVenda de " + Relatorio.formatarDinheiro(removida.getValor())
                        + " para " + removida.getCliente() + " foi removida.");
            } else {
                System.out.println("\nNúmero inválido.");
            }
        } catch (NumberFormatException e) {
            System.out.println("\nEntrada inválida. Digite um número válido.");
        }
    }

    private void opcaoRelatorioEspecifico() {
        System.out.print("Digite o mês (YYYY-MM): ");
        String mes = ENTRADA.nextLine().trim();
        GerenciadorVendas g = new GerenciadorVendas(mes);

        if (!new File(g.nomeArquivo).exists()) {
            System.out.println("Nenhum relatório encontrado para esse período.");
            return;
        }

        new RelatorioMensal(g).exibir();
    }

    private void opcaoComparar() {
        System.out.print("Digite o primeiro mês (YYYY-MM): ");
        String mes1 = ENTRADA.nextLine().trim();
        System.out.print("Digite o segundo mês (YYYY-MM): ");
        String mes2 = ENTRADA.nextLine().trim();

        new RelatorioComparativo(mes1, mes2).exibir();
    }

    void executar() {
        while (true) {
            System.out.println("\n===== MENU =====");
            System.out.println("[1]  Adicionar novas vendas no mês atual");
            System.out.println("[2]  Ver todas as vendas do mês atual");
            System.out.println("[3]  Apagar uma venda do mês atual");
            System.out.println("[4]  Ver relatório do mês atual");
            System.out.println("[5]  Ver relatório de um mês específico");
            System.out.println("[6]  Comparar dois meses");
            System.out.println("[7]  Sair");

            System.out.print("Escolha uma opção: ");
            String opcao = ENTRADA.nextLine().trim();

            switch (opcao) {
                case "1":
                    opcaoAdicionar();
                    break;
                case "2":
                    exibirLista();
                    break;
                case "3":
                    opcaoApagar();
                    break;
                case "4":
                    this.relatorio.exibir();
                    break;
                case "5":
                    opcaoRelatorioEspecifico();
                    break;
                case "6":
                    opcaoComparar();
                    break;
                case "7":
                    System.out.println("Saindo do programa...");
                    return;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        }
    }

    public static void main(String[] args) {

        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        SistemaVendas sistema = new SistemaVendas();
        sistema.executar();
    }
}

class JsonMini {

    static String escreverListaDeObjetos(List<Map<String, Object>> lista) {
        if (lista.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < lista.size(); i++) {
            Map<String, Object> obj = lista.get(i);
            sb.append("    {\n");
            int j = 0;
            for (Map.Entry<String, Object> e : obj.entrySet()) {
                sb.append("        ").append(escreverString(e.getKey()))
                  .append(": ").append(escreverValor(e.getValue()));
                if (j < obj.size() - 1) sb.append(",");
                sb.append("\n");
                j++;
            }
            sb.append("    }");
            if (i < lista.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escreverValor(Object valor) {
        if (valor instanceof String) {
            return escreverString((String) valor);
        }
        return String.valueOf(valor);
    }

    private static String escreverString(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:   sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }

    private final String s;
    private int i;

    private JsonMini(String s) {
        this.s = s;
        this.i = 0;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> parseListaDeObjetos(String texto) {
        JsonMini p = new JsonMini(texto);
        Object valor = p.lerValor();
        List<Map<String, Object>> resultado = new ArrayList<>();
        if (valor instanceof List) {
            for (Object o : (List<Object>) valor) {
                if (o instanceof Map) {
                    resultado.add((Map<String, Object>) o);
                }
            }
        }
        return resultado;
    }

    private Object lerValor() {
        pularEspacos();
        char c = s.charAt(i);
        if (c == '[') return lerArray();
        if (c == '{') return lerObjeto();
        if (c == '"') return lerString();
        if (c == 't' || c == 'f') return lerBooleano();
        if (c == 'n') { i += 4; return null; }
        return lerNumero();
    }

    private List<Object> lerArray() {
        List<Object> lista = new ArrayList<>();
        i++;
        pularEspacos();
        if (s.charAt(i) == ']') { i++; return lista; }
        while (true) {
            lista.add(lerValor());
            pularEspacos();
            char c = s.charAt(i++);
            if (c == ']') break;
        }
        return lista;
    }

    private Map<String, Object> lerObjeto() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        i++;
        pularEspacos();
        if (s.charAt(i) == '}') { i++; return mapa; }
        while (true) {
            pularEspacos();
            String chave = lerString();
            pularEspacos();
            i++;
            Object valor = lerValor();
            mapa.put(chave, valor);
            pularEspacos();
            char c = s.charAt(i++);
            if (c == '}') break;
        }
        return mapa;
    }

    private String lerString() {
        StringBuilder sb = new StringBuilder();
        i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/');  break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object lerBooleano() {
        if (s.charAt(i) == 't') { i += 4; return Boolean.TRUE; }
        i += 5;
        return Boolean.FALSE;
    }

    private Double lerNumero() {
        int inicio = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
                i++;
            } else {
                break;
            }
        }
        return Double.parseDouble(s.substring(inicio, i));
    }

    private void pularEspacos() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
            else break;
        }
    }
}
