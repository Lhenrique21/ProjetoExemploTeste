import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.htmlunit.Page;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextInput;
import org.junit.jupiter.api.Test;


/**
 * Verifica o cadastro e a listagem de especialidades
 * (rotas GET /especialidade, GET /especialidade/novo e POST /especialidade).
 */
class TesteEspecialidade extends BaseTeste {

    @Test
    void listagemFicaVaziaQuandoNaoExistemEspecialidades() throws Exception {
        HtmlPage listagem = abrir("/especialidade");

        assertEquals(200, listagem.getWebResponse().getStatusCode());
        assertEquals(0, contar(listagem, ".itemEspecialidade"));
    }

    @Test
    void formularioDeCadastroTemOsCamposEsperados() throws Exception {
        HtmlPage pagina = abrir("/especialidade/novo");

        assertEquals(1, pagina.getForms().size());
        HtmlForm formulario = primeiroFormulario(pagina);
        assertEquals("/especialidade", formulario.getActionAttribute());
        assertEquals("POST", formulario.getMethodAttribute().toUpperCase());

        // O campo é encontrado pelo nome (no formulário) e pelo id (na página)
        HtmlTextInput campo = formulario.getInputByName("descricao");
        assertNotNull(pagina.getElementById("descricao"));
        assertEquals("descricao", campo.getId());
        assertTrue(campo.hasAttribute("required"), "O campo descrição deveria ser obrigatório");
        assertNotNull(botaoEnviar(pagina));
    }

    @Test
    void cadastrarEspecialidadeValidaFazOItemAparecerNaListagem() throws Exception {
        HtmlPage pagina = abrir("/especialidade/novo");
        HtmlTextInput campo = primeiroFormulario(pagina).getInputByName("descricao");

        campo.type("Cardiologia");
        // O clique envia o formulário (POST). O servidor responde com redirecionamento
        // e o HtmlUnit segue sozinho até a listagem, que é a página devolvida.
        HtmlPage listagem = botaoEnviar(pagina).click();

        assertEquals("/especialidade", listagem.getUrl().getPath());
        assertEquals(1, contar(listagem, ".itemEspecialidade"));
        assertTrue(listagem.asNormalizedText().contains("Cardiologia"));
    }

    @Test
    void formularioVazioNaoEEnviadoPeloNavegador() throws Exception {
        HtmlPage pagina = abrir("/especialidade/novo");
        HtmlForm formulario = primeiroFormulario(pagina);

        // O campo é "required" e está vazio, então o formulário é inválido
        assertFalse(formulario.isValid());

        // Como num navegador de verdade, clicar em Cadastrar não envia nada
        HtmlPage depoisDoClique = botaoEnviar(pagina).click();
        assertEquals("/especialidade/novo", depoisDoClique.getUrl().getPath());

        // Confirma que nenhuma especialidade foi gravada
        assertEquals(0, contar(abrir("/especialidade"), ".itemEspecialidade"));
    }

    @Test
    void cadastroResponde302ParaAListagemEGravaOItem() throws Exception {
        // Desliga o redirecionamento automático para podermos ver a resposta 302
        navegador.getOptions().setRedirectEnabled(false);
        navegador.getOptions().setThrowExceptionOnFailingStatusCode(false);

        Page resposta = enviarPost("/especialidade", "descricao", "Ortopedia");

        assertEquals(302, resposta.getWebResponse().getStatusCode());
        assertEquals("/especialidade", resposta.getWebResponse().getResponseHeaderValue("Location"));

        // Mesmo sem seguir o redirecionamento, o item foi gravado
        navegador.getOptions().setRedirectEnabled(true);
        assertEquals(1, contar(abrir("/especialidade"), ".itemEspecialidade"));
    }

    /**
     * COMPORTAMENTO ATUAL DA APLICAÇÃO (não é o ideal):
     * a validação existe só no navegador (atributo required). O servidor não confere nada,
     * então uma requisição enviada direto, com descrição vazia, é aceita e gravada.
     */
    @Test
    void servidorAceitaDescricaoVaziaQuandoEnviadaDiretamente() throws Exception {
        HtmlPage listagem = enviarPost("/especialidade", "descricao", "");

        assertEquals(1, contar(listagem, ".itemEspecialidade"));
    }
}
