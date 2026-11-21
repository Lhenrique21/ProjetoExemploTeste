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
import org.htmlunit.html.HtmlNumberInput;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.Test;

/**
 * Verifica o cadastro e a listagem de consultas.
 * Uma consulta liga um paciente a um médico (relacionamentos um para muitos).
 */
class TesteConsulta extends BaseTeste {

    @Test
    void listagemFicaVaziaQuandoNaoExistemConsultas() throws Exception {
        HtmlPage listagem = abrir("/consulta");

        assertEquals(200, listagem.getWebResponse().getStatusCode());
        assertEquals(0, contar(listagem, ".itemConsulta"));
    }

    @Test
    void camposDePacienteEMedicoFicamVaziosQuandoNaoHaCadastros() throws Exception {
        HtmlPage pagina = abrir("/consulta/novo");
        HtmlForm formulario = primeiroFormulario(pagina);

        assertEquals(0, formulario.getSelectByName("pacienteId").getOptions().size());
        assertEquals(0, formulario.getSelectByName("medicoId").getOptions().size());
    }

    @Test
    void formularioMostraOsPacientesEMedicosJaCadastrados() throws Exception {
        cadastrarEspecialidade("Cardiologia");
        cadastrarMedico("Pedro Alves", "Cardiologia");
        cadastrarPaciente("Ana", "Diabetes");
        cadastrarPaciente("Bruno", "Hipertensão");

        HtmlPage pagina = abrir("/consulta/novo");
        HtmlForm formulario = primeiroFormulario(pagina);
        HtmlSelect selectPaciente = formulario.getSelectByName("pacienteId");
        HtmlSelect selectMedico = formulario.getSelectByName("medicoId");

        assertEquals(2, selectPaciente.getOptions().size());
        assertEquals(1, selectMedico.getOptions().size());

        List<String> pacientes = new ArrayList<>();
        for (HtmlOption opcao : selectPaciente.getOptions()) {
            pacientes.add(opcao.asNormalizedText().trim());
        }
        assertTrue(pacientes.contains("Ana"));
        assertTrue(pacientes.contains("Bruno"));
    }

    @Test
    void cadastrarConsultaMostraHoraPacienteEMedicoNaListagem() throws Exception {
        cadastrarEspecialidade("Cardiologia");
        cadastrarMedico("Pedro Alves", "Cardiologia");
        cadastrarPaciente("Maria Souza", "Check-up anual");

        HtmlPage pagina = abrir("/consulta/novo");
        HtmlForm formulario = primeiroFormulario(pagina);
        selecionarOpcao(formulario.getSelectByName("pacienteId"), "Maria Souza");
        selecionarOpcao(formulario.getSelectByName("medicoId"), "Pedro Alves");
        HtmlNumberInput campoHora = formulario.getInputByName("hora");
        campoHora.type("14");
        HtmlPage listagem = botaoEnviar(pagina).click();

        assertEquals("/consulta", listagem.getUrl().getPath());
        assertEquals(1, contar(listagem, ".itemConsulta"));
        assertEquals("Hora: 14", listagem.querySelector(".badgeInfo").asNormalizedText());
        String texto = listagem.querySelector(".itemConsulta").asNormalizedText();
        assertTrue(texto.contains("Maria Souza"));
        assertTrue(texto.contains("Pedro Alves"));
    }

    @Test
    void cadaConsultaFicaLigadaAoSeuPacienteEAoSeuMedico() throws Exception {
        cadastrarEspecialidade("Cardiologia");
        cadastrarMedico("Pedro Alves", "Cardiologia");
        cadastrarMedico("Luiza Prado", "Cardiologia");
        cadastrarPaciente("Ana", "Diabetes");
        cadastrarPaciente("Bruno", "Hipertensão");

        cadastrarConsulta("Ana", "Pedro Alves", "9");
        cadastrarConsulta("Bruno", "Luiza Prado", "15");

        HtmlPage listagem = abrir("/consulta");
        DomNodeList<DomNode> consultas = listagem.querySelectorAll(".itemConsulta");
        assertEquals(2, consultas.size());

        for (DomNode consulta : consultas) {
            String texto = consulta.asNormalizedText();

            if (texto.contains("Hora: 9")) {
                assertTrue(texto.contains("Ana") && texto.contains("Pedro Alves"));
                assertFalse(texto.contains("Bruno") || texto.contains("Luiza Prado"));
            } else if (texto.contains("Hora: 15")) {
                assertTrue(texto.contains("Bruno") && texto.contains("Luiza Prado"));
                assertFalse(texto.contains("Ana") || texto.contains("Pedro Alves"));
            } else {
                fail("Consulta inesperada na listagem: " + texto);
            }
        }
    }

    @Test
    void campoHoraNaoAceitaLetras() throws Exception {
        HtmlPage pagina = abrir("/consulta/novo");
        HtmlNumberInput campoHora = primeiroFormulario(pagina).getInputByName("hora");

        assertEquals("number", campoHora.getAttribute("type"));
        assertTrue(campoHora.hasAttribute("required"));

        campoHora.type("abc");
        assertEquals("", campoHora.getValue(), "Letras não deveriam ser aceitas num campo numérico");
    }

    @Test
    void formularioSemHoraNaoEEnviadoPeloNavegador() throws Exception {
        cadastrarEspecialidade("Cardiologia");
        cadastrarMedico("Pedro Alves", "Cardiologia");
        cadastrarPaciente("Ana", "Diabetes");

        HtmlPage pagina = abrir("/consulta/novo");
        HtmlForm formulario = primeiroFormulario(pagina);
        selecionarOpcao(formulario.getSelectByName("pacienteId"), "Ana");
        selecionarOpcao(formulario.getSelectByName("medicoId"), "Pedro Alves");

        HtmlPage depoisDoClique = botaoEnviar(pagina).click(); // a hora ficou vazia

        assertEquals("/consulta/novo", depoisDoClique.getUrl().getPath());
        assertEquals(0, contar(abrir("/consulta"), ".itemConsulta"));
    }

    /**
     * COMPORTAMENTO ATUAL DA APLICAÇÃO:
     * o banco tem chave estrangeira. Informar um paciente e um médico que não existem
     * faz o INSERT falhar e o servidor responde com erro 500 (em vez de uma mensagem amigável).
     */
    @Test
    void consultaComPacienteEMedicoInexistentesDaErro500ENadaEGravado() throws Exception {
        navegador.getOptions().setThrowExceptionOnFailingStatusCode(false);

        Page resposta = enviarPost("/consulta", "pacienteId", "9999", "medicoId", "9999", "hora", "10");

        assertEquals(500, resposta.getWebResponse().getStatusCode());
        assertEquals(0, contar(abrir("/consulta"), ".itemConsulta"));
    }

    /**
     * COMPORTAMENTO ATUAL DA APLICAÇÃO (não é o ideal):
     * as chaves estrangeiras aceitam valor nulo, então o servidor grava uma consulta
     * sem paciente e sem médico quando esses campos não são enviados.
     */
    @Test
    void servidorAceitaConsultaSemPacienteESemMedicoQuandoEnviadaDiretamente() throws Exception {
        HtmlPage listagem = enviarPost("/consulta", "hora", "10");

        assertEquals(1, contar(listagem, ".itemConsulta"));
        assertEquals("Hora: 10", listagem.querySelector(".badgeInfo").asNormalizedText());
    }
}
