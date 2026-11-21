import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextInput;
import org.junit.jupiter.api.Test;
import org.htmlunit.html.DomElement;

/**
 * Verifica o cadastro e a listagem de pacientes.
 * Cada paciente é cadastrado junto com o seu prontuário (relacionamento um para um).
 */
class TestePaciente extends BaseTeste {

    @Test
    void listagemFicaVaziaQuandoNaoExistemPacientes() throws Exception {
        HtmlPage listagem = abrir("/paciente");

        assertEquals(200, listagem.getWebResponse().getStatusCode());
        assertEquals(0, contar(listagem, ".itemPaciente"));
    }

    @Test
    void formularioTemOsCamposDoPacienteEDoProntuario() throws Exception {
        HtmlPage pagina = abrir("/paciente/novo");
        HtmlForm formulario = primeiroFormulario(pagina);

        HtmlTextInput campoNome = formulario.getInputByName("nome");
        HtmlTextInput campoDescricao = formulario.getInputByName("descricao");

        assertTrue(campoNome.hasAttribute("required"));
        assertTrue(campoDescricao.hasAttribute("required"));
        assertEquals("Prontuário", pagina.querySelector(".tituloSecao").asNormalizedText());
    }

    @Test
    void cadastrarPacienteMostraNomeEProntuarioNaListagem() throws Exception {
        HtmlPage pagina = abrir("/paciente/novo");
        HtmlForm formulario = primeiroFormulario(pagina);
        HtmlTextInput campoNome = formulario.getInputByName("nome");
        HtmlTextInput campoDescricao = formulario.getInputByName("descricao");

        campoNome.type("José da Silva"); // com acento, para testar a codificação dos caracteres
        campoDescricao.type("Alergia a dipirona");
        HtmlPage listagem = botaoEnviar(pagina).click();

        assertEquals("/paciente", listagem.getUrl().getPath());
        assertEquals(1, contar(listagem, ".itemPaciente"));
        assertEquals("José da Silva", listagem.querySelector(".nomePaciente").asNormalizedText());
        assertEquals("Alergia a dipirona", listagem.querySelector(".descricaoProntuario").asNormalizedText());
    }

    @Test
    void cadaPacienteFicaComOSeuProprioProntuario() throws Exception {
        cadastrarPaciente("Ana", "Diabetes");
        cadastrarPaciente("Bruno", "Hipertensão");

        HtmlPage listagem = abrir("/paciente");
        DomNodeList<DomNode> itens = listagem.querySelectorAll(".itemPaciente");

        assertEquals(2, itens.size());
        for (DomNode item : itens) {
            DomElement element = (DomElement) item;
            String nome = element.querySelector(".nomePaciente", item).asNormalizedText();
            String prontuario = element.querySelector(".descricaoProntuario", item).asNormalizedText();

            if (nome.equals("Ana")) {
                assertEquals("Diabetes", prontuario);
            } else if (nome.equals("Bruno")) {
                assertEquals("Hipertensão", prontuario);
            } else {
                fail("Paciente inesperado na listagem: " + nome);
            }
        }
    }

    @Test
    void formularioSemProntuarioNaoEEnviadoPeloNavegador() throws Exception {
        HtmlPage pagina = abrir("/paciente/novo");
        HtmlTextInput campoNome = primeiroFormulario(pagina).getInputByName("nome");

        campoNome.type("Carla"); // falta a descrição do prontuário (campo obrigatório)
        HtmlPage depoisDoClique = botaoEnviar(pagina).click();

        assertEquals("/paciente/novo", depoisDoClique.getUrl().getPath());
        assertEquals(0, contar(abrir("/paciente"), ".itemPaciente"));
    }

    /**
     * COMPORTAMENTO ATUAL DA APLICAÇÃO (não é o ideal):
     * o servidor não valida os campos. Enviando direto, um paciente com nome
     * e prontuário vazios é gravado.
     */
    @Test
    void servidorAceitaPacienteComCamposVaziosQuandoEnviadoDiretamente() throws Exception {
        HtmlPage listagem = enviarPost("/paciente", "nome", "", "descricao", "");

        assertEquals(1, contar(listagem, ".itemPaciente"));
    }
}
