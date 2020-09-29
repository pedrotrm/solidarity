package com.solidarity.api.services;

import com.solidarity.api.dto.EntidadeDTO;
import com.solidarity.api.dto.EntidadeNewDTO;
import com.solidarity.api.dto.VagaDTO;
import com.solidarity.api.dto.VoluntarioDTO;
import com.solidarity.api.model.*;
import com.solidarity.api.model.enums.Causa;
import com.solidarity.api.repositories.*;
import com.solidarity.api.services.exception.DataIntegrityException;
import com.solidarity.api.services.exception.ObjectNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EntidadeService {

    private final EntidadeRepository repository;
    private final EnderecoRepository enderecoRepository;
    private final VagaRepository vagaRepository;
    private final CidadeRepository cidadeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final S3Service s3Service;

    public EntidadeService(EntidadeRepository repository,
                           EnderecoRepository enderecoRepository,
                           VagaRepository vagaRepository,
                           CidadeRepository cidadeRepository,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService,
                           S3Service s3Service) {
        this.repository = repository;
        this.enderecoRepository = enderecoRepository;
        this.vagaRepository = vagaRepository;
        this.cidadeRepository = cidadeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.s3Service = s3Service;
    }

    public List<Entidade> findAll() {
        return repository.findAll();
    }

    public Entidade findById(Long id) {
        Optional<Entidade> obj = repository.findById(id);
        return obj.orElseThrow(() -> new ObjectNotFoundException(
                "Objeto não encontrado! Id: " + id + ", Tipo: " + Entidade.class.getName()));
    }

    public Vaga findVagaById(Long id){
        Optional<Vaga> obj = vagaRepository.findById(id);
        return obj.orElseThrow(() -> new ObjectNotFoundException(
                "Objeto não encontrado! Id: " + id + ", Tipo: " + Vaga.class.getName()));
    }

    public Set<VoluntarioDTO> findVagaVoluntarios(Long vagaId) {
        Vaga vaga = findVagaById(vagaId);
        return vaga.getVagas().stream()
                .map(x -> new VoluntarioDTO(x.getVoluntario()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<VagaDTO> findAllVagas(Long entidadeId){
        List<Vaga> obj = vagaRepository.findAllVagasByEntidadeId(entidadeId).orElseThrow(() -> new ObjectNotFoundException(
                "Objeto não encontrado! Tipo: " + Vaga.class.getName()));
        return obj.stream()
                .map(VagaDTO::new)
                .collect(Collectors.toSet());
    }

    @Transactional
    public Entidade insert(Entidade obj) {
        obj.setId(null);
        obj = repository.save(obj);
        enderecoRepository.save(obj.getEndereco());
        return obj;
    }

    @Transactional
    public Entidade update(Entidade obj) {
        Entidade newObj = findById(obj.getId());
        updateData(newObj, obj);
        repository.save(newObj);
        enderecoRepository.save(newObj.getEndereco());
        return newObj;
    }

    @Transactional
    public void createVaga(Vaga vaga){
        vaga.setId(null);
        vagaRepository.createVaga(vaga);
        enderecoRepository.save(vaga.getEnderecoVaga());
    }

    @Transactional
    public void updateVaga(Vaga obj){
        Vaga newObj = findVagaById(obj.getId());
        Endereco endereco = enderecoRepository.findById(newObj.getEnderecoVaga().getId()).orElseThrow();
        updateVaga(newObj, obj, endereco);
        vagaRepository.updateVaga(newObj);
        enderecoRepository.save(endereco);
    }


    public void delete(Long id) {
        findById(id);
        try {
            repository.deleteById(id);
        }
        catch (Exception e) {
            throw new DataIntegrityException("Não é possível excluir um cliente com vagas associoadas");
        }
    }

    public URI uploadFotoPerfil(MultipartFile multipartFile, Long entidadeId){
        URI uri = s3Service.uploadFile(multipartFile);
        Entidade entidade = findById(entidadeId);
        entidade.setFotoPerfil(uri.toString());
        repository.save(entidade);
        return uri;
    }

    public Page<Entidade> findPage(Integer page, Integer linesPerPage, String orderBy, String direction) {
        PageRequest pageRequest = PageRequest.of(page, linesPerPage, Sort.Direction.valueOf(direction), orderBy);
        return repository.findAll(pageRequest);
    }

    public Entidade fromDTO(EntidadeDTO objDto) {
        Cidade cid = new Cidade(objDto.getCidadeId(), null, null);
        Endereco end = new Endereco(null, objDto.getLogadouro(),objDto.getNumero(),objDto.getComplemento(),objDto.getBairro(),objDto.getCep(),cid);
        return new Entidade(objDto.getId(),objDto.getNome(), objDto.getEmail(),objDto.getCnpj(),Causa.valorDe(objDto.getCausa1()),Causa.valorDe(objDto.getCausa2()), objDto.getDescricao(), end);

    }

    public Entidade fromDTO(EntidadeNewDTO objDto){
        Cidade cidade = new Cidade(objDto.getCidadeId(), null, null);
        Endereco endereco = new Endereco(null, objDto.getLogadouro(),objDto.getNumero(),
                objDto.getComplemento(),objDto.getBairro(),objDto.getCep(),cidade);
        Usuario usuario = new Usuario(null ,objDto.getNome(), objDto.getEmail(), passwordEncoder.encode(objDto.getSenha()));
        usuario.setPermissoes(getPermissoesEntidade());
        Entidade entidade = new Entidade(null ,objDto.getNome(), objDto.getEmail(),
                objDto.getCnpj(),Causa.valorDe(objDto.getCausa1()),Causa.valorDe(objDto.getCausa2()),
                objDto.getDescricao(), endereco, usuario);
        entidade.getTelefones().add(objDto.getTelefone1());
        if (objDto.getTelefone2()!=null) {
            entidade.getTelefones().add(objDto.getTelefone2());
        }
        if (objDto.getTelefone3()!=null) {
            entidade.getTelefones().add(objDto.getTelefone3());
        }
        return entidade;
    }

    public Vaga fromVagaDTO(VagaDTO objDto){
        Cidade cidade = cidadeRepository.findById((objDto.getCidadeId())).orElseThrow(() -> new ObjectNotFoundException(
                "Objeto não encontrado! Id: " + objDto.getCidadeId() + ", Tipo: " + Cidade.class.getName()));
        Endereco endereco = new Endereco(null, objDto.getLogadouro(), objDto.getNumero(), objDto.getComplemento(), objDto.getBairro(), objDto.getCep(), cidade);
        Entidade entidade = findById(objDto.getEntidadeId());
        return new Vaga(null, objDto.getNome(), objDto.getDescricao(), objDto.getCausa1().getCode(), objDto.getCausa2().getCode(), objDto.getHabilidade().getCode(), objDto.getDataInicio(), objDto.getDataFim(),objDto.getTipoVaga().getCode(), objDto.getQuantidade(), endereco, entidade);
    }

    private void updateData(Entidade newObj, Entidade obj){
        newObj.setNome(obj.getNome());
        newObj.setEmail(obj.getEmail());
        newObj.setCnpj(obj.getCnpj());
        newObj.setCausa1(obj.getCausa1());
        newObj.setCausa2(obj.getCausa2());
        newObj.setEndereco(obj.getEndereco());
    }

    private void updateVaga(Vaga newObj, Vaga obj, Endereco objEndereco) {
        newObj.setNome(obj.getNome());
        newObj.setDescricao(obj.getDescricao());
        newObj.setCausa1(obj.getCausa1());
        newObj.setCausa1(obj.getCausa2());
        newObj.setHabilidade(obj.getHabilidade());
        newObj.setDataInicio(obj.getDataInicio());
        newObj.setDataFim(obj.getDataFim());
        newObj.setTipoVaga(obj.getTipoVaga());
        newObj.setQuantidade(obj.getQuantidade());
        objEndereco.setLogadouro(obj.getEnderecoVaga().getLogadouro());
        objEndereco.setNumero(obj.getEnderecoVaga().getNumero());
        objEndereco.setBairro(obj.getEnderecoVaga().getBairro());
        objEndereco.setCep(obj.getEnderecoVaga().getCep());
        objEndereco.setComplemento(obj.getEnderecoVaga().getComplemento());
        objEndereco.setCidade(obj.getEnderecoVaga().getCidade());
    }

    private List<Permissao> getPermissoesEntidade(){
        List<Permissao> permissoes = new ArrayList<>();
        permissoes.add(new Permissao(3L));
        permissoes.add(new Permissao(4L));
        permissoes.add(new Permissao(7L));
        permissoes.add(new Permissao(8L));
        permissoes.add(new Permissao(9L));
        permissoes.add(new Permissao(10L));
        permissoes.add(new Permissao(15L));
        permissoes.add(new Permissao(16L));
        permissoes.add(new Permissao(17L));
        return permissoes;
    }


}
