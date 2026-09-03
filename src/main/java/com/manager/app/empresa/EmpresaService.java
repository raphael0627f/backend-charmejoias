package com.manager.app.empresa;

import com.manager.app.endereco.Endereco;
import com.manager.app.notafiscal.client.ContoraClient;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.manager.app.endereco.EnderecoService;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository repository;
    private final EnderecoService enderecoService;
    private final EmpresaValidation validation;
    private final ContoraClient contoraClient;

    public Page<Empresa> findAll(Pageable pageable){
        return repository.findAll(pageable);
    }

    public Empresa findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Empresa não encontrada. id: " + id));
    }

    @Transactional
    public Empresa create(Empresa empresa){
        validation.cnpjExistenteCreate(empresa.getCnpj());

        empresa.setEndereco(enderecoService.create(empresa.getEndereco()));
        empresa.setProximoNumeroNfe(1L);
        empresa.setSerieNfe(1);
        empresa.setCertificadoEnviado(false);

        Empresa empresaSalva = repository.save(empresa);
        empresaSalva.setContoraCompanyId(contoraClient.criarEmpresa(empresaSalva));

        return empresaSalva;
    }

    @Transactional
    public Empresa update(Empresa empresa){
        validation.cnpjExistenteUpdate(empresa.getCnpj(), empresa.getId());
        Empresa empresaUpdate = this.findById(empresa.getId());
        Endereco endereco = enderecoService.update(empresa.getEndereco());

        empresaUpdate.setRazaoSocial(empresa.getRazaoSocial());
        empresaUpdate.setNomeFantasia(empresa.getNomeFantasia());
        empresaUpdate.setCnpj(empresa.getCnpj());
        empresaUpdate.setInscricaoEstadual(empresa.getInscricaoEstadual());
        empresaUpdate.setCnae(empresa.getCnae());
        empresaUpdate.setRegimeTributario(empresa.getRegimeTributario());
        empresaUpdate.setEndereco(endereco);

        if (empresa.getSerieNfe() != null) {
            empresaUpdate.setSerieNfe(empresa.getSerieNfe());
        }
        if (empresa.getProximoNumeroNfe() != null) {
            empresaUpdate.setProximoNumeroNfe(empresa.getProximoNumeroNfe());
        }

        if (empresaUpdate.getContoraCompanyId() != null) {
            contoraClient.atualizarEmpresa(empresaUpdate.getContoraCompanyId(), empresaUpdate);
        }

        return empresaUpdate;
    }

    @Transactional
    public Empresa enviarCertificado(Long id, MultipartFile certificado, String senha){
        Empresa empresa = this.findById(id);
        if (empresa.getContoraCompanyId() == null) {
            throw new ObjetoNaoEncontradoException("Empresa ainda não cadastrada na Contora. id: " + id);
        }

        contoraClient.enviarCertificado(empresa.getContoraCompanyId(), certificado, senha);

        empresa.setCertificadoEnviado(true);
        empresa.setCertificadoEnviadoEm(LocalDateTime.now());
        return empresa;
    }
}
