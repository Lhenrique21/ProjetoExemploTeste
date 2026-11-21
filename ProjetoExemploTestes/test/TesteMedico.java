import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.htmlunit.Page;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlTextInput;
import org.junit.jupiter.api.Test;
import org.htmlunit.html.DomElement;

/**
 * Verifica o cadastro e a listagem de médicos.
 * Um médico pode ter várias especialidades (relacionamento muitos para muitos).
 */
class TesteMedico extends BaseTeste {

    @Test
    void listagemFicaVaziaQuandoNaoExistemMedicos() throws Exception {
        HtmlPage listagem = abrir("/medico");

        assertEquals(200, listagem.getWebResponse().getStatusCode());
        assertEquals(0, contar(listagem, ".itemMedico"));
    }

    @Test
    void campoDeEspecialidadesFicaVazioQuandoNaoExistemEspecialidades() throws Exception {
        HtmlPage pagina = abrir("/medico/novo");
        HtmlSelect select = primeiroFormulario(pagina).getSelectByName("especialidades");

        assertEquals(0, select.getOptions().size());
    }

    @Test
    void formularioMostraAsEspecialidadesJaCadastradas() throws Exception {
        cadastrarEspecialidade("Cardiologia");
        cadastrarEspecialidade("Pediatria");

        HtmlPage pagina = abrir("/medico/novo");
        HtmlSelect select = primeiroFormulario(pagina).getSelectByName("especialidades");

        assertTrue(select.isMultipleSelectEnabled(), "O select deveria permitir várias opções");
        assertEquals(2, select.getOptions().size());

        List<String> textosDasOpcoes = new ArrayList<>();
        for (HtmlOption opcao : select.getOptions()) {
            textosDasOpcoes.add(opcao.asNormalizedText().trim());
        }
        assertTrue(textosDasOpcoes.contains("Cardiologia"));
        assertTrue(textosDasOpcoes.contains("Pediatria"));
    }

    @Test
    void cadastrarMedicoComUmaEspecialidadeMostraOsDoisNaListagem() throws Exception {
        cadastrarEspecialidade("Cardiologia");

        cadastrarMedico("Pedro Alves", "Cardiologia");

        HtmlPage listagem = abrir("/medico");
        assertEquals(1, contar(listagem, ".itemMedico"));
        assertEquals("Pedro Alves", listagem.querySelector(".nomeMedico").asNormalizedText());
        assertEquals("Cardiologia", listagem.querySelector(".itemEspecialidade").asNormalizedText());
    }

    @Test
    void cadaMedicoFicaComAsSuasEspecialidades() throws Exception {
        cadastrarEspecialidade("Cardiologia");
        cadastrarEspecialidade("Pediatria");
        cadastrarEspecialidade("Ortopedia");

        // Helena tem duas especialidades (seleção múltipla); Paulo tem uma
        cadastrarMedico("Helena Costa", "Cardiologia", "Pediatria");
        cadastrarMedico("Paulo Souza", "Ortopedia");

        HtmlPage listagem = abrir("/medico");
        DomNodeList<DomNode> medicos = listagem.querySelectorAll(".itemMedico");
        assertEquals(2, medicos.size());

        for (DomNode item : medicos) {
        DomElement medico = (DomElement) item;
        String texto = medico.asNormalizedText();
        // Cada especialidade aparece como um <li> dentro do médico
        int quantidadeEspecialidades = medico.getElementsByTagName("li").size();

        if (texto.contains("Helena Costa")) {
            assertEquals(2, quantidadeEspecialidades);
            assertTrue(texto.contains("Cardiologia"));
            assertTrue(texto.contains("Pediatria"));
            assertFalse(texto.contains("Ortopedia"));
        }   else if (texto.contains("Paulo Souza")) {
            assertEquals(1, quantidadeEspecialidades);
            assertTrue(texto.contains("Ortopedia"));
        }       else {
        fail("Médico inesperado na listagem: " + texto);
    }
}
    }

    @Test
    void medicoExigeNomeEEspecialidadeParaSerEnviadoPeloNavegador() throws Exception {
        cadastrarEspecialidade("Cardiologia");
        HtmlPage pagina = abrir("/medico/novo");
        HtmlForm formulario = primeiroFormulario(pagina);
        HtmlTextInput campoNome = formulario.getInputByName("nome");
        HtmlSelect select = formulario.getSelectByName("especialidades");

        // 1) Tudo vazio: o envio é bloqueado
        HtmlPage resultado = botaoEnviar(pagina).click();
        assertEquals("/medico/novo", resultado.getUrl().getPath());

        // 2) Só a especialidade escolhida (sem nome): continua bloqueado
        selecionarOpcao(select, "Cardiologia");
        resultado = botaoEnviar(pagina).click();
        assertEquals("/medico/novo", resultado.getUrl().getPath());

        // 3) Com nome e especialidade, o formulário fica válido e o envio funciona
        campoNome.type("Ana Lima");
        assertTrue(campoNome.isValid());
        HtmlPage listagem = botaoEnviar(pagina).click();

        assertEquals("/medico", listagem.getUrl().getPath());
        assertEquals(1, contar(listagem, ".itemMedico")); // só um: os envios bloqueados não gravaram nada
    }

    /**
     * COMPORTAMENTO INESPERADO DA APLICAÇÃO (bug encontrado):
     * o app.js grava o médico ANTES de procurar as especialidades. Sem o campo
     * "especialidades" a busca falha, o servidor responde 500, mas o médico já foi salvo.
     */
    @Test
    void envioDeMedicoSemEspecialidadeDaErro500EMesmoAssimGravaOMedico() throws Exception {
        navegador.getOptions().setThrowExceptionOnFailingStatusCode(false);

        Page resposta = enviarPost("/medico", "nome", "Carlos Lima");

        assertEquals(500, resposta.getWebResponse().getStatusCode());
        HtmlPage listagem = abrir("/medico");
        assertEquals(1, contar(listagem, ".itemMedico"), "O médico ficou gravado apesar do erro");
        assertEquals(0, contar(listagem, ".itemEspecialidade"));
    }

    @Test
    void envioDeMedicoSemNomeDaErro500ENadaEGravado() throws Exception {
        cadastrarEspecialidade("Cardiologia");
        navegador.getOptions().setThrowExceptionOnFailingStatusCode(false);

        // Na primeira execução do banco de teste, a especialidade criada tem id 1
        Page resposta = enviarPost("/medico", "especialidades", "1");

        // No modelo, o nome do médico não pode ser nulo (allowNull: false)
        assertEquals(500, resposta.getWebResponse().getStatusCode());
        assertEquals(0, contar(abrir("/medico"), ".itemMedico"));
    }

    /**
     * COMPORTAMENTO ATUAL DA APLICAÇÃO (não é o ideal):
     * se o identificador da especialidade não existe, o servidor não reclama:
     * grava o médico sem nenhuma especialidade.
     */
    @Test
    void especialidadeInexistenteEIgnoradaEOMedicoEGravadoSemEspecialidades() throws Exception {
        HtmlPage listagem = enviarPost("/medico", "nome", "Marta Reis", "especialidades", "9999");

        assertEquals(1, contar(listagem, ".itemMedico"));
        assertEquals(0, contar(listagem, ".itemEspecialidade"));
    }
}
