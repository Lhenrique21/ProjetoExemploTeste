import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;

/**
 * Verifica a página inicial, os links entre as páginas e o comportamento
 * da aplicação quando a rota não existe (erro 404).
 */
class TesteNavegacao extends BaseTeste {

    @Test
    void paginaInicialCarregaComTituloEQuatroCards() throws Exception {
        HtmlPage pagina = abrir("/");

        assertEquals(200, pagina.getWebResponse().getStatusCode());
        assertEquals("Sistema Hospitalar", pagina.getTitleText());
        assertEquals("Sistema Hospitalar", pagina.querySelector(".tituloPrincipal").asNormalizedText());
        // Um card para cada entidade: Médicos, Especialidades, Pacientes e Consultas
        assertEquals(4, contar(pagina, ".card"));
    }

    @Test
    void linksDaPaginaInicialApontamParaAsRotasCorretas() throws Exception {
        HtmlPage pagina = abrir("/");
        String[] rotasEsperadas = {
                "/medico", "/medico/novo",
                "/especialidade", "/especialidade/novo",
                "/paciente", "/paciente/novo",
                "/consulta", "/consulta/novo"
        };

        for (String rota : rotasEsperadas) {
            // getAnchorByHref lança exceção se o link não existir
            assertNotNull(pagina.getAnchorByHref(rota), "Link não encontrado: " + rota);
        }
        assertEquals(8, pagina.getAnchors().size());
    }

    @Test
    void todasAsListagensAbremComStatus200ETituloCorreto() throws Exception {
        String[][] paginas = {
                {"/medico", "Listar Médicos"},
                {"/especialidade", "Listar Especialidades"},
                {"/paciente", "Listar Pacientes"},
                {"/consulta", "Listar Consultas"}
        };

        for (String[] pagina : paginas) {
            String rota = pagina[0];
            String tituloEsperado = pagina[1];

            HtmlPage resposta = abrir(rota);

            assertEquals(200, resposta.getWebResponse().getStatusCode(), "Status inesperado em " + rota);
            assertEquals(tituloEsperado, resposta.getTitleText(), "Título inesperado em " + rota);
        }
    }

    @Test
    void clicarNoLinkDaPaginaInicialAbreAListagemDeMedicos() throws Exception {
        HtmlPage inicial = abrir("/");

        HtmlAnchor link = inicial.getAnchorByHref("/medico");
        HtmlPage listagem = link.click();

        assertEquals("/medico", listagem.getUrl().getPath());
        assertEquals("Listar Médicos", listagem.getTitleText());
    }

    @Test
    void clicarEmCadastrarEspecialidadeAbreOFormulario() throws Exception {
        HtmlPage inicial = abrir("/");

        // Aqui o link é encontrado por seletor CSS, em vez de getAnchorByHref
        HtmlAnchor link = (HtmlAnchor) inicial.querySelector("a[href='/especialidade/novo']");
        HtmlPage formulario = link.click();

        assertEquals("/especialidade/novo", formulario.getUrl().getPath());
        assertEquals("Cadastrar Especialidade", formulario.getTitleText());
    }

    @Test
    void botaoVoltarDaListagemLevaParaAPaginaInicial() throws Exception {
        HtmlPage listagem = abrir("/paciente");

        HtmlPage inicial = listagem.getAnchorByHref("/").click();

        assertEquals("Sistema Hospitalar", inicial.getTitleText());
    }

    @Test
    void botaoCancelarDoFormularioLevaParaAListagem() throws Exception {
        HtmlPage formulario = abrir("/especialidade/novo");

        HtmlPage listagem = formulario.getAnchorByHref("/especialidade").click();

        assertEquals("Listar Especialidades", listagem.getTitleText());
    }

    @Test
    void rotaInexistenteLancaExcecaoComStatus404() {
        // Por padrão, o HtmlUnit lança exceção quando o servidor responde com erro (404, 500...)
        FailingHttpStatusCodeException erro = assertThrows(
                FailingHttpStatusCodeException.class,
                () -> navegador.getPage(urlBase + "/rota-que-nao-existe"));

        assertEquals(404, erro.getStatusCode());
    }

    @Test
    void rotaInexistenteRetorna404QuandoAExcecaoEstaDesligada() throws Exception {
        navegador.getOptions().setThrowExceptionOnFailingStatusCode(false);

        Page resposta = navegador.getPage(urlBase + "/rota-que-nao-existe");

        assertEquals(404, resposta.getWebResponse().getStatusCode());
        // Mensagem padrão do Express para rotas que não existem
        assertTrue(resposta.getWebResponse().getContentAsString().contains("Cannot GET"));
    }
}
