package br.com.farmaetiquetas.farma_api.dto;

import java.util.ArrayList;
import java.util.List;

public class PedidoDTO {
    public String cliente;
    public String rg;
    public String cnpj;
    public String telefone;
    public String endereco;
    public String atendente;
    public List<MedicamentoDTO> medicamentos = new ArrayList<>();

    public static class MedicamentoDTO {
        public String qtd;
        public String desc;
        public String laboratorio;
    }
}