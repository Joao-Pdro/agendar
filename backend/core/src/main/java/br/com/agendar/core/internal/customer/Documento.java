package br.com.agendar.core.internal.customer;

/**
 * CPF e CNPJ: normalização e validação do dígito verificador.
 *
 * <p>Guardado só com dígitos. Aceita entrada com pontos, traços e barra, porque é assim que o
 * documento é digitado.
 */
public final class Documento {

    private Documento() {
    }

    /**
     * Remove tudo que não é dígito.
     */
    public static String normalizar(String documento) {
        return documento == null ? "" : documento.replaceAll("\\D", "");
    }

    /**
     * Verdadeiro só para CPF (11 dígitos) ou CNPJ (14) com dígito verificador válido.
     *
     * <p>Sequências repetidas como {@code 11111111111} passam no cálculo do dígito, mas não são
     * documentos válidos — são rejeitadas explicitamente.
     */
    public static boolean valido(String documento) {
        String digitos = normalizar(documento);
        if (digitos.length() == 11) {
            return validoCpf(digitos);
        }
        if (digitos.length() == 14) {
            return validoCnpj(digitos);
        }
        return false;
    }

    private static boolean validoCpf(String cpf) {
        if (todosIguais(cpf)) {
            return false;
        }
        for (int posicao = 9; posicao < 11; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) {
                soma += Character.getNumericValue(cpf.charAt(i)) * (posicao + 1 - i);
            }
            int digito = 11 - (soma % 11);
            if (digito >= 10) {
                digito = 0;
            }
            if (digito != Character.getNumericValue(cpf.charAt(posicao))) {
                return false;
            }
        }
        return true;
    }

    private static boolean validoCnpj(String cnpj) {
        if (todosIguais(cnpj)) {
            return false;
        }
        int[] pesosPrimeiro = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesosSegundo = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int primeiro = digitoCnpj(cnpj, pesosPrimeiro);
        if (primeiro != Character.getNumericValue(cnpj.charAt(12))) {
            return false;
        }
        int segundo = digitoCnpj(cnpj, pesosSegundo);
        return segundo == Character.getNumericValue(cnpj.charAt(13));
    }

    private static int digitoCnpj(String cnpj, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.getNumericValue(cnpj.charAt(i)) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean todosIguais(String documento) {
        char primeiro = documento.charAt(0);
        for (int i = 1; i < documento.length(); i++) {
            if (documento.charAt(i) != primeiro) {
                return false;
            }
        }
        return true;
    }
}
