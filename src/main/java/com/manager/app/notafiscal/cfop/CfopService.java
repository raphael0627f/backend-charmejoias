package com.manager.app.notafiscal.cfop;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CfopService {

    private final CfopRepository repository;

    public List<Cfop> findAll(){
        return repository.findAll(Sort.by("codigo"));
    }
}
