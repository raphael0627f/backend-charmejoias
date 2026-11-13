package com.manager.app.planejadorrota;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteRepository;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.endereco.Endereco;
import com.manager.app.endereco.EnderecoRepository;
import com.manager.app.pedido.Pedido;
import com.manager.app.pedido.PedidoRepository;
import com.manager.app.pedido.StatusPedido;
import com.manager.app.planejadorrota.dto.PlanejadorRotaClienteRequestDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaPedidoItemRequestDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaRequestDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanejadorRotaPedidosDeixarTest {

    @Mock PlanejadorRotaRepository repository;
    @Mock ClienteRepository clienteRepository;
    @Mock EnderecoRepository enderecoRepository;
    @Mock com.manager.app.consignacao.ConsignacaoRepository consignacaoRepository;
    @Mock PedidoRepository pedidoRepository;
    @Spy PlanejadorRotaValidation validation = new PlanejadorRotaValidation();
    @Mock com.manager.app.usuario.UsuarioResumoMapper usuarioResumoMapper;
    @Mock com.manager.app.security.UsuarioAuditorAware usuarioAuditorAware;
    @Mock PlanejadorRotaMapper mapper;
    @Mock PlanejadorRotaDetalhes detalhes;
    @InjectMocks PlanejadorRotaService service;

    private Cliente cliente;
    private Cliente outroCliente;
    private Endereco endereco;

    @BeforeEach
    void setUp() {
        endereco = new Endereco();
        endereco.setId(5L);
        endereco.setLogradouro("Rua A");
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setCodigo("C1");
        cliente.setNome("Maria");
        cliente.setEnderecos(new ArrayList<>(List.of(endereco)));
        outroCliente = new Cliente();
        outroCliente.setId(UUID.randomUUID());
        when(clienteRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        when(enderecoRepository.findById(5L)).thenReturn(Optional.of(endereco));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
    }

    private Pedido pedido(Long id, Cliente dono, StatusPedido status) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setCliente(dono);
        p.setStatus(status);
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(p));
        when(pedidoRepository.getReferenceById(id)).thenReturn(p);
        return p;
    }

    private PlanejadorRotaRequestDTO request(Long... pedidoIds) {
        PlanejadorRotaClienteRequestDTO item = new PlanejadorRotaClienteRequestDTO();
        item.setClienteId(cliente.getId());
        item.setEnderecoId(5L);
        List<PlanejadorRotaPedidoItemRequestDTO> pedidos = new ArrayList<>();
        for (Long id : pedidoIds) {
            PlanejadorRotaPedidoItemRequestDTO p = new PlanejadorRotaPedidoItemRequestDTO();
            p.setPedidoId(id);
            pedidos.add(p);
        }
        item.setPedidosDeixar(pedidos);
        PlanejadorRotaRequestDTO req = new PlanejadorRotaRequestDTO();
        req.setNome("Rota");
        req.setItens(List.of(item));
        return req;
    }

    @Test
    void salvaPedidoSeparadoDoMesmoCliente() {
        pedido(10L, cliente, StatusPedido.SEPARADO);
        PlanejadorRota rota = service.create(request(10L));
        assertThat(rota.getItens().get(0).getPedidosDeixar()).hasSize(1);
        assertThat(rota.getItens().get(0).getPedidosDeixar().get(0).getPedido().getId()).isEqualTo(10L);
    }

    @Test
    void rotaSemPedidosContinuaFuncionando() {
        PlanejadorRotaRequestDTO req = request();
        req.getItens().get(0).setPedidosDeixar(null);
        assertThat(service.create(req).getItens().get(0).getPedidosDeixar()).isEmpty();
    }

    @Test
    void pedidoDeOutroClienteRecusado() {
        pedido(11L, outroCliente, StatusPedido.SEPARADO);
        assertThatThrownBy(() -> service.create(request(11L))).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("O pedido Nº 11 não pertence ao cliente selecionado.");
    }

    @Test
    void pedidoNaoSeparadoRecusado() {
        pedido(12L, cliente, StatusPedido.ENVIADO);
        assertThatThrownBy(() -> service.create(request(12L))).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("O pedido Nº 12 não está com o status Separado e não pode ser deixado na rota.");
    }

    @Test
    void pedidoRepetidoRecusado() {
        pedido(13L, cliente, StatusPedido.SEPARADO);
        assertThatThrownBy(() -> service.create(request(13L, 13L))).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("O pedido Nº 13 foi informado mais de uma vez para o mesmo cliente.");
    }

    @Test
    void pedidoInexistenteRecusado() {
        when(pedidoRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(99L))).isInstanceOf(ObjetoNaoEncontradoException.class);
    }

    @Test
    void pedidoJaGravadoQueVirouEnviadoPermaneceNaEdicao() {
        Pedido p = pedido(14L, cliente, StatusPedido.SEPARADO);
        PlanejadorRota rota = service.create(request(14L));
        PlanejadorRotaCliente item = rota.getItens().get(0);
        item.setId(1L);
        rota.setId(3L);
        when(repository.findById(3L)).thenReturn(Optional.of(rota));
        p.setStatus(StatusPedido.ENVIADO);

        PlanejadorRotaRequestDTO req = request(14L);
        req.setId(3L);
        req.getItens().get(0).setId(1L);
        req.getItens().get(0).getPedidosDeixar().get(0).setConfirmada(true);
        PlanejadorRota atualizada = service.update(req);

        assertThat(atualizada.getItens().get(0).getPedidosDeixar()).hasSize(1);
        assertThat(atualizada.getItens().get(0).getPedidosDeixar().get(0).getConfirmada()).isTrue();

        // mas um pedido ENVIADO que ainda nao estava na rota continua sendo recusado
        pedido(15L, cliente, StatusPedido.ENVIADO);
        PlanejadorRotaRequestDTO novo = request(14L, 15L);
        novo.setId(3L);
        novo.getItens().get(0).setId(1L);
        assertThatThrownBy(() -> service.update(novo)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void xlsxListaPedidosEConsignacoesComMontagemNaColunaDeDeixar() throws Exception {
        PlanejadorRotaDetalhes det = mock(PlanejadorRotaDetalhes.class);
        when(det.montagemPorConsignacao(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of(8756L, 830L));
        PlanejadorRotaXlsxService xlsx = new PlanejadorRotaXlsxService(det);

        PlanejadorRota rota = new PlanejadorRota();
        rota.setId(1L);
        rota.setNome("Rota X");
        PlanejadorRotaCliente item = new PlanejadorRotaCliente();
        item.setCliente(cliente);
        item.setEndereco(endereco);
        item.setOrdem(1);
        Consignacao c = new Consignacao();
        c.setId(8756L);
        PlanejadorRotaConsignacaoDeixar cd = new PlanejadorRotaConsignacaoDeixar();
        cd.setConsignacao(c);
        item.adicionarConsignacaoDeixar(cd);
        Consignacao semMontagem = new Consignacao();
        semMontagem.setId(8800L);
        PlanejadorRotaConsignacaoTrazer ct = new PlanejadorRotaConsignacaoTrazer();
        ct.setConsignacao(semMontagem);
        item.adicionarConsignacaoTrazer(ct);
        PlanejadorRotaPedidoDeixar pd = new PlanejadorRotaPedidoDeixar();
        pd.setPedido(pedido(20L, cliente, StatusPedido.SEPARADO));
        item.adicionarPedidoDeixar(pd);
        rota.adicionarItem(item);

        Sheet sheet = new XSSFWorkbook(new ByteArrayInputStream(xlsx.gerarXlsx(rota))).getSheetAt(0);
        assertThat(sheet.getRow(0).getCell(17).getStringCellValue()).isEqualTo("Deixar (documentos)");
        assertThat(sheet.getRow(1).getCell(17).getStringCellValue())
                .isEqualTo("Consignação Nº 8756 (Montagem Nº 830)\nPedido Nº 20");
        assertThat(sheet.getRow(1).getCell(18).getStringCellValue()).isEqualTo("Consignação Nº 8800");
        // IDA conta consignacao e pedido; a coluna Referencia e as demais continuam
        assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(1);
        assertThat(sheet.getRow(0).getCell(14).getStringCellValue()).isEqualTo("Referência");
    }
}
