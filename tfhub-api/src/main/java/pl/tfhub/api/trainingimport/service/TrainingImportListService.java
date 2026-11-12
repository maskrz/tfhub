package pl.tfhub.api.trainingimport.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tfhub.api.common.authorization.AccessScopeService;
import pl.tfhub.api.trainingimport.dto.response.TrainingImportListItemResponse;
import pl.tfhub.api.trainingimport.enums.TrainingImportStatus;
import pl.tfhub.api.trainingimport.repository.TrainingImportRepository;

@Service
public class TrainingImportListService {

	private static final List<TrainingImportStatus> ACTIVE_STATUSES = List.of(
			TrainingImportStatus.PENDING,
			TrainingImportStatus.PROCESSING,
			TrainingImportStatus.REVIEW_REQUIRED,
			TrainingImportStatus.FAILED);

	private final TrainingImportRepository importRepository;
	private final AccessScopeService accessScopeService;

	public TrainingImportListService(
			TrainingImportRepository importRepository,
			AccessScopeService accessScopeService) {
		this.importRepository = importRepository;
		this.accessScopeService = accessScopeService;
	}

	@Transactional(readOnly = true)
	public List<TrainingImportListItemResponse> listActive() {
		accessScopeService.requireCoachOrAdmin();
		boolean admin = accessScopeService.isAdmin();
		List<Long> groupIds = admin
				? List.of(-1L)
				: accessScopeService.querySafeIds(accessScopeService.assignedGroupIds());
		return importRepository.findActiveForGroups(ACTIVE_STATUSES, admin, groupIds);
	}
}
