package br.com.farmaetiquetas.farma_api.Service;

import br.com.farmaetiquetas.farma_api.dto.PedidoDTO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

@Service
public class PedidoService {

    private final JdbcTemplate jdbcTemplate;

    // O Spring Boot injeta o banco de dados aqui automaticamente
    public PedidoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PedidoDTO buscarPedido(String numPedido) {
        PedidoDTO dto = new PedidoDTO();

        try {
            // 1. Busca Cliente e Vendedor (cadcvend)
            String sqlVenda = "SELECT cod_cliente, cod_vendedor FROM cadcvend WHERE num_nota::text = ?";
            Map<String, Object> venda = jdbcTemplate.queryForMap(sqlVenda, numPedido);
            String codCliente = safeStr(venda.get("cod_cliente"));
            String codVendedor = safeStr(venda.get("cod_vendedor"));

            // 2. Busca Dados do Cliente (cadclien)
            String sqlCliente = "SELECT nom_cliente, num_cnpj, num_ident, end_cliente, num_endereco, bai_cliente, cid_cliente, num_celular, org_emissor, est_emissor, est_cliente, cep_cliente FROM cadclien WHERE cod_cliente::text = ?";
            Map<String, Object> clienteMap = jdbcTemplate.queryForMap(sqlCliente, codCliente);

            dto.cliente = safeStr(clienteMap.get("nom_cliente"));
            dto.cnpj = safeStr(clienteMap.get("num_cnpj"));
            dto.rg = safeStr(clienteMap.get("num_ident"));
            dto.telefone = safeStr(clienteMap.get("num_celular"));

            dto.endereco = String.format("%s, %s, %s - %s-%s %s",
                    safeStr(clienteMap.get("end_cliente")), safeStr(clienteMap.get("num_endereco")),
                    safeStr(clienteMap.get("bai_cliente")), safeStr(clienteMap.get("cid_cliente")),
                    safeStr(clienteMap.get("est_cliente")), safeStr(clienteMap.get("cep_cliente")));

            // 3. Busca Atendente (cadusuar)
            if (!codVendedor.isEmpty()) {
                try {
                    String sqlVendedor = "SELECT nom_apelido FROM cadusuar WHERE cod_usuario::text = ?";
                    Map<String, Object> vendedorMap = jdbcTemplate.queryForMap(sqlVendedor, codVendedor);
                    dto.atendente = safeStr(vendedorMap.get("nom_apelido"));
                } catch (EmptyResultDataAccessException e) {
                    dto.atendente = "";
                }
            }

            // 4. Busca Medicamentos (Com JOIN otimizado!)
            String sqlMedicamentos = "SELECT i.qtd_produto, p.nom_produto, l.nom_laborat " +
                    "FROM cadivend i " +
                    "JOIN cadprodu p ON p.cod_reduzido::text = i.cod_reduzido::text " +
                    "LEFT JOIN cadlabor l ON l.cod_laborat::text = p.cod_laborat::text " +
                    "WHERE i.num_nota::text = ? " +
                    "AND (i.flg_excluido = '' OR i.flg_excluido IS NULL) " +
                    "AND p.cod_grupo IN (86,98,99,111,102)";

            List<Map<String, Object>> listaMed = jdbcTemplate.queryForList(sqlMedicamentos, numPedido);

            for (Map<String, Object> row : listaMed) {
                PedidoDTO.MedicamentoDTO med = new PedidoDTO.MedicamentoDTO();
                med.desc = safeStr(row.get("nom_produto"));
                med.laboratorio = safeStr(row.get("nom_laborat"));
                med.qtd = formatarQtd(safeStr(row.get("qtd_produto")));
                dto.medicamentos.add(med);
            }

            return dto;

        } catch (EmptyResultDataAccessException e) {
            // Se não achar o pedido, retorna null para o Controller saber
            return null;
        }
    }

    // Funções auxiliares mantidas do seu código original
    private String safeStr(Object obj) {
        return obj == null ? "" : obj.toString().trim();
    }

    private String formatarQtd(String qtdStr) {
        String qtdFmt = "1";
        if (!qtdStr.isEmpty()) {
            try {
                double qtdDouble = Double.parseDouble(qtdStr);
                if (qtdDouble == Math.floor(qtdDouble)) qtdFmt = String.valueOf((int) qtdDouble);
                else qtdFmt = new DecimalFormat("#.##").format(qtdDouble);
            } catch (NumberFormatException ex) {
                qtdFmt = qtdStr;
            }
        }
        return qtdFmt;
    }
}