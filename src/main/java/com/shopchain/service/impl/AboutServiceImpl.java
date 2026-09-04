package com.shopchain.service.impl;

import com.shopchain.repository.AboutContentRepository;
import com.shopchain.service.AboutService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AboutServiceImpl implements AboutService {

    private final AboutContentRepository aboutContentRepository;

    public AboutServiceImpl(AboutContentRepository aboutContentRepository) {
        this.aboutContentRepository = aboutContentRepository;
    }

    @Override
    public Optional<String> getAboutContent() {
        return aboutContentRepository.findFirstByOrderByUpdatedAtDesc()
                .map(aboutContent -> aboutContent.getContent());
    }
}
