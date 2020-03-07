package com.solidarity.solidarity_backend.resources;


import com.solidarity.solidarity_backend.DTO.VoluntarioDTO;
import com.solidarity.solidarity_backend.DTO.VoluntarioNewDTO;
import com.solidarity.solidarity_backend.model.Voluntario;
import com.solidarity.solidarity_backend.services.VoluntarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value ="/voluntarios")
public class VoluntarioResource {

    @Autowired
    private VoluntarioService service;

    @GetMapping(value = "/{id}")
    public ResponseEntity<Voluntario> findById(@PathVariable Long id){
        Voluntario obj =  service.findById(id);
        return ResponseEntity.ok().body(obj);
    }

    @RequestMapping(method=RequestMethod.POST)
    public ResponseEntity<Void> insert(@Valid @RequestBody VoluntarioNewDTO objDto) {
        Voluntario obj = service.fromDTO(objDto);
        obj = service.insert(obj);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(obj.getId()).toUri();
        return ResponseEntity.created(uri).build();
    }

    @RequestMapping(value="/{id}", method=RequestMethod.PUT)
    public ResponseEntity<Void> update(@Valid @RequestBody VoluntarioDTO objDto, @PathVariable Long id) {
        Voluntario obj = service.fromDTO(objDto);
        obj.setId(id);
        obj = service.update(obj);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value="/{id}", method=RequestMethod.DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(method= RequestMethod.GET)
    public ResponseEntity<List<VoluntarioDTO>> findAll() {
        List<Voluntario> list = service.findAll();
        List<VoluntarioDTO> listDto = list.stream().map(obj -> new VoluntarioDTO(obj)).collect(Collectors.toList());
        return ResponseEntity.ok().body(listDto);
    }

    @RequestMapping(value="/page", method=RequestMethod.GET)
    public ResponseEntity<Page<VoluntarioDTO>> findPage(
            @RequestParam(value="page", defaultValue="0") Integer page,
            @RequestParam(value="linesPerPage", defaultValue="24") Integer linesPerPage,
            @RequestParam(value="orderBy", defaultValue="nome") String orderBy,
            @RequestParam(value="direction", defaultValue="ASC") String direction) {
        Page<Voluntario> list = service.findPage(page, linesPerPage, orderBy, direction);
        Page<VoluntarioDTO> listDto = list.map(obj -> new VoluntarioDTO(obj));
        return ResponseEntity.ok().body(listDto);
    }


}
