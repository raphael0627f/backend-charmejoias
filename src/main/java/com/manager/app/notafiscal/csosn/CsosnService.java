package com.manager.app.notafiscal.csosn;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CsosnService {

    private final CsosnRepository repository;

    public List<Csosn> findAll(){
        return repository.findAll(Sort.by("codigo"));
    }
}
