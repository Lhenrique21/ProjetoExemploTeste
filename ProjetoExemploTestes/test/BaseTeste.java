import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlNumberInput;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlTextInput;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;


public abstract class BaseTeste {

    // Deixa o console limpo: desliga os avisos do HtmlUnit (por exemplo, sobre CSS).
    private static final Logger LOG_HTMLUNIT = Logger.getLogger("org.htmlunit");
    static {
        LOG_HTMLUNIT.setLevel(Level.OFF);
    }

    /** O "navegador" do HtmlUnit. Os testes usam este objeto para abrir páginas. */
    protected WebClient navegador;

    /** Endereço do servidor de teste, por exemplo http://127.0.0.1:54321 */
    protected String urlBase;

    private Process servidor;
    private Path arquivoBanco;
    private Path arquivoLog;

    // ------------------------------------------------------------------
    // Preparação e limpeza (rodam automaticamente antes/depois de cada teste)
    // ------------------------------------------------------------------

    @BeforeEach
    void iniciarServidorENavegador() throws Exception {
        int porta = descobrirPortaLivre();
        urlBase = "http://127.0.0.1:" + porta;

        // Tudo que o teste cria fica dentro da pasta "target" (que o Maven já usa).
        Path pastaTarget = Path.of(System.getProperty("user.dir"), "target");
        Files.createDirectories(pastaTarget);
        arquivoBanco = pastaTarget.resolve("bd-teste-" + porta + ".sqlite");
        arquivoLog = pastaTarget.resolve("servidor-teste-" + porta + ".log");
        Files.deleteIfExists(arquivoBanco);

        // Executa "node app.js" usando as variáveis PORT e BD_STORAGE,
        // que o app.js e o config/bd.js passaram a aceitar.
        ProcessBuilder pb = new ProcessBuilder("node", "app.js");
        pb.directory(new File(System.getProperty("user.dir")));
        pb.environment().put("PORT", String.valueOf(porta));
        pb.environment().put("BD_STORAGE", arquivoBanco.toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(arquivoLog.toFile()); // o que o servidor escreve vai para o arquivo de log

        try {
            servidor = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Não consegui executar o comando 'node'. O Node.js está instalado?", e);
        }

        esperarServidorFicarPronto();
        navegador = new WebClient();
    }

    @AfterEach
    void encerrarServidorENavegador() throws Exception {
        if (navegador != null) {
            navegador.close();
        }
        if (servidor != null) {
            servidor.destroy();
            if (!servidor.waitFor(5, TimeUnit.SECONDS)) {
                servidor.destroyForcibly();
            }
        }
        if (arquivoBanco != null) {
            try {
                Files.deleteIfExists(arquivoBanco);
            } catch (IOException e) {
                // Se não der para apagar, não tem problema: fica dentro de "target".
            }
        }
    }

    private int descobrirPortaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Espera o app.js escrever, no log, que o servidor subiu e o banco foi criado. */
    private void esperarServidorFicarPronto() throws Exception {
        for (int tentativa = 0; tentativa < 150; tentativa++) { // até 30 segundos
            if (!servidor.isAlive()) {
                throw new IllegalStateException("O servidor Node encerrou sozinho. "
                        + "Você rodou 'npm install' antes? Log do servidor:\n" + lerLog());
            }
            String log = lerLog();
            if (log.contains("Servidor em execu") && log.contains("sincronizado")) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("O servidor Node demorou demais para iniciar. Log:\n" + lerLog());
    }

    private String lerLog() throws IOException {
        if (!Files.exists(arquivoLog)) {
            return "";
        }
        return new String(Files.readAllBytes(arquivoLog), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Métodos de apoio (para os testes ficarem curtos e fáceis de ler)
    // ------------------------------------------------------------------

    /** Abre uma página da aplicação. Exemplo: abrir("/medico") */
    protected HtmlPage abrir(String caminho) throws IOException {
        return navegador.getPage(urlBase + caminho);
    }

    /** Primeiro formulário da página (todas as telas de cadastro têm apenas um). */
    protected HtmlForm primeiroFormulario(HtmlPage pagina) {
        return pagina.getForms().get(0);
    }

    /** Botão "Cadastrar" (button do tipo submit) da página. Usa um seletor CSS. */
    protected HtmlButton botaoEnviar(HtmlPage pagina) {
        return (HtmlButton) pagina.querySelector("button[type='submit']");
    }

    /** Quantos elementos da página combinam com o seletor CSS. Exemplo: contar(pagina, ".itemMedico") */
    protected int contar(HtmlPage pagina, String seletorCss) {
        return pagina.querySelectorAll(seletorCss).size();
    }

    /** Seleciona, num campo select, a opção que tem o texto informado. */
    protected void selecionarOpcao(HtmlSelect select, String texto) throws IOException {
        for (HtmlOption opcao : select.getOptions()) {
            if (opcao.asNormalizedText().trim().equals(texto)) {
                select.setSelectedAttribute(opcao, true);
                return;
            }
        }
        throw new IllegalArgumentException("Não existe a opção \"" + texto + "\" no select");
    }

    /**
     * Envia um POST diretamente ao servidor, sem passar pelo formulário.
     * Serve para simular um cliente que "pula" a validação do navegador (campo required).
     * Uso: enviarPost("/medico", "nome", "Ana", "especialidades", "1")  -> pares nome, valor.
     */
    protected <P extends Page> P enviarPost(String caminho, String... camposEValores) throws IOException {
        WebRequest requisicao = new WebRequest(URI.create(urlBase + caminho).toURL(), HttpMethod.POST);
        List<NameValuePair> parametros = new ArrayList<>();
        for (int i = 0; i < camposEValores.length; i += 2) {
            parametros.add(new NameValuePair(camposEValores[i], camposEValores[i + 1]));
        }
        requisicao.setRequestParameters(parametros);
        return navegador.getPage(requisicao);
    }

    // ------------------------------------------------------------------
    // Cadastros feitos pela tela, como um usuário faria (preparam dados para os testes)
    // ------------------------------------------------------------------

    protected void cadastrarEspecialidade(String descricao) throws IOException {
        HtmlPage pagina = abrir("/especialidade/novo");
        HtmlTextInput campo = primeiroFormulario(pagina).getInputByName("descricao");
        campo.type(descricao);
        botaoEnviar(pagina).click();
    }

    protected void cadastrarPaciente(String nome, String descricaoProntuario) throws IOException {
        HtmlPage pagina = abrir("/paciente/novo");
        HtmlForm formulario = primeiroFormulario(pagina);
        HtmlTextInput campoNome = formulario.getInputByName("nome");
        HtmlTextInput campoDescricao = formulario.getInputByName("descricao");
        campoNome.type(nome);
        campoDescricao.type(descricaoProntuario);
        botaoEnviar(pagina).click();
    }

    /** As especialidades informadas já precisam estar cadastradas. */
    protected void cadastrarMedico(String nome, String... especialidades) throws IOException {
        HtmlPage pagina = abrir("/medico/novo");
        HtmlForm formulario = primeiroFormulario(pagina);
        HtmlTextInput campoNome = formulario.getInputByName("nome");
        campoNome.type(nome);
        HtmlSelect select = formulario.getSelectByName("especialidades");
        for (String especialidade : especialidades) {
            selecionarOpcao(select, especialidade);
        }
        botaoEnviar(pagina).click();
    }

    /** O paciente e o médico informados já precisam estar cadastrados. */
    protected void cadastrarConsulta(String paciente, String medico, String hora) throws IOException {
        HtmlPage pagina = abrir("/consulta/novo");
        HtmlForm formulario = primeiroFormulario(pagina);
        selecionarOpcao(formulario.getSelectByName("pacienteId"), paciente);
        selecionarOpcao(formulario.getSelectByName("medicoId"), medico);
        HtmlNumberInput campoHora = formulario.getInputByName("hora");
        campoHora.type(hora);
        botaoEnviar(pagina).click();
    }
}
